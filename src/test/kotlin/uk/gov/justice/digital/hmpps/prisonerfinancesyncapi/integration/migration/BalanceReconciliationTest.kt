package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.utils.isMoneyEqual
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

class BalanceReconciliationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should correctly calculate balance using only transactions after the latest migration timestamp`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonNumber = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val privateCashAccountCode = 2101
    val holdsGLAccountCode = 2199

    val preMigrationTransactionAmount = BigDecimal("20.00")
    val postMigrationTransactionAmount = BigDecimal("15.00")

    val migrationTimestamp = LocalDateTime.now().minus(5, ChronoUnit.DAYS)

    val initialAvailableBalance = BigDecimal("100.00")
    val initialHoldBalance = BigDecimal("50.00")

    val preMigrationTransaction = SyncOffenderTransactionRequest(
      transactionId = Random.nextLong(),
      caseloadId = prisonId,
      transactionTimestamp = migrationTimestamp.minusDays(1),
      createdAt = migrationTimestamp.minusDays(1),
      createdBy = "PRE_MIG_USER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 2609628,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 1227181,
          subAccountType = "REG",
          postingType = "CR",
          type = "POST",
          description = "Money Through Post (pre-migration)",
          amount = preMigrationTransactionAmount,
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = privateCashAccountCode, postingType = "CR", amount = preMigrationTransactionAmount),
            GeneralLedgerEntry(entrySequence = 2, code = holdsGLAccountCode, postingType = "DR", amount = preMigrationTransactionAmount),
          ),
        ),
      ),
      requestId = UUID.randomUUID(),
      createdByDisplayName = "PRE_MIG_USER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(preMigrationTransaction))
      .exchange()
      .expectStatus().isCreated

    val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonId,
          accountCode = privateCashAccountCode,
          balance = initialAvailableBalance,
          holdBalance = initialHoldBalance,
          asOfTimestamp = migrationTimestamp,
          transactionId = 1234L,
        ),
      ),
    )

    webTestClient
      .post()
      .uri("/migrate/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(prisonerMigrationRequestBody))
      .exchange()
      .expectStatus().isOk

    val postMigrationTransaction = SyncOffenderTransactionRequest(
      transactionId = Random.nextLong(),
      caseloadId = prisonId,
      transactionTimestamp = migrationTimestamp.plusDays(1),
      createdAt = migrationTimestamp.plusDays(1),
      createdBy = "POST_MIG_USER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 2609628,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 1227181,
          subAccountType = "REG",
          postingType = "CR",
          type = "CASH",
          description = "Cash Deposit (post-migration)",
          amount = postMigrationTransactionAmount,
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = privateCashAccountCode, postingType = "CR", amount = postMigrationTransactionAmount),
            GeneralLedgerEntry(entrySequence = 2, code = holdsGLAccountCode, postingType = "DR", amount = postMigrationTransactionAmount),
          ),
        ),
      ),
      requestId = UUID.randomUUID(),
      createdByDisplayName = "POST_MIG_USER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(postMigrationTransaction))
      .exchange()
      .expectStatus().isCreated

    // The pre-migration transaction of £20.00 should be ignored.
    // The final balance should be the initial balance (£100.00) + the post-migration transaction (£15.00).
    val expectedFinalBalance = initialAvailableBalance.add(postMigrationTransactionAmount)
    val expectedFinalHoldBalance = initialHoldBalance

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, privateCashAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isMoneyEqual(expectedFinalBalance)
      .jsonPath("$.holdBalance").isMoneyEqual(expectedFinalHoldBalance)
  }
}
