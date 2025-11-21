package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.holds

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

class MigratePrisonerWithHoldBalancesTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should migrate point in time balances including holds and apply a new hold correctly`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonNumber = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val privateCashAccountCode = 2101
    val initialAvailableBalance = BigDecimal("37.00")
    val initialHoldBalance = BigDecimal("3.00")
    val newHoldAmount = BigDecimal("5.00")
    val holdsGLAccountCode = 2199

    val migrateTimestamp = LocalDateTime.of(2025, 9, 18, 16, 0, 0)
    val transactionTimestamp = LocalDateTime.of(2025, 9, 18, 17, 0, 0)

    val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonId,
          accountCode = privateCashAccountCode,
          balance = initialAvailableBalance,
          holdBalance = initialHoldBalance,
          asOfTimestamp = migrateTimestamp,
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

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, privateCashAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(initialAvailableBalance.toDouble())
      .jsonPath("$.holdBalance").isEqualTo(initialHoldBalance.toDouble())

    val addHoldRequest = SyncOffenderTransactionRequest(
      transactionId = Random.nextLong(),
      caseloadId = prisonId,
      transactionTimestamp = transactionTimestamp,
      createdAt = transactionTimestamp,
      createdBy = "SOME_USER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 2609628,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 1227181,
          subAccountType = "REG",
          postingType = "DR",
          type = "HOA",
          description = "HOLD",
          amount = newHoldAmount.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(
              entrySequence = 1,
              code = privateCashAccountCode,
              postingType = "DR",
              amount = newHoldAmount.toDouble(),
            ),
            GeneralLedgerEntry(
              entrySequence = 2,
              code = holdsGLAccountCode,
              postingType = "CR",
              amount = newHoldAmount.toDouble(),
            ),
          ),
        ),
      ),
      requestId = UUID.randomUUID(),
      createdByDisplayName = "SOME_USER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(addHoldRequest))
      .exchange()
      .expectStatus().isCreated

    val expectedFinalAvailableBalance = initialAvailableBalance.subtract(newHoldAmount)
    val expectedFinalHoldBalance = initialHoldBalance.add(newHoldAmount)

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, privateCashAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(expectedFinalAvailableBalance.toDouble())
      .jsonPath("$.holdBalance").isEqualTo(expectedFinalHoldBalance.toDouble())
  }
}
