package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniquePrisonNumber
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

class DuplicateMigrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val privateCashAccountCode = 2101
  private val holdsGlAccountCode = 2199

  @Test
  fun `should NOT double count when same migration request is sent twice and correctly reflect subsequent HOA`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonNumber = uniquePrisonNumber()

    val migrationTimestamp = LocalDateTime.now().minus(7, ChronoUnit.DAYS)
    val initialBalanceAmount = BigDecimal("100.00")
    val postMigrationHoldAmount = BigDecimal("15.00")

    val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonId,
          accountCode = privateCashAccountCode,
          balance = initialBalanceAmount,
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = migrationTimestamp,
          transactionId = 9000L,
        ),
      ),
    )

    // Initial Migration (Superseded)
    webTestClient
      .post()
      .uri("/migrate/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(prisonerMigrationRequestBody))
      .exchange()
      .expectStatus().isOk

    // Run Duplicate Migration (Latest)
    webTestClient
      .post()
      .uri("/migrate/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(prisonerMigrationRequestBody))
      .exchange()
      .expectStatus().isOk

    // Post-Migration Activity (HOA: Moves 15.00 from Available to Hold)
    val postMigrationTimestamp = migrationTimestamp.plusDays(1)
    val transactionRequest = createSyncRequest(
      caseloadId = prisonId,
      timestamp = postMigrationTimestamp,
      offenderDisplayId = prisonNumber,
      offenderAccountCode = privateCashAccountCode,
      offenderSubAccountType = "REG",
      offenderPostingType = "DR",
      amount = postMigrationHoldAmount,
      transactionType = "HOA",
      glCode = holdsGlAccountCode,
      glPostingType = "CR",
    )
    postSyncTransaction(transactionRequest)

    // Expected Balance = Latest Migration (100.00) - HOA (15.00) = 85.00
    // Expected Hold Balance = Initial Hold (0.00) + HOA (15.00) = 15.00
    val expectedFinalBalance = initialBalanceAmount.subtract(postMigrationHoldAmount)
    val expectedFinalHoldBalance = postMigrationHoldAmount

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

  private fun postSyncTransaction(syncRequest: SyncOffenderTransactionRequest) {
    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(objectMapper.writeValueAsString(syncRequest))
      .exchange()
      .expectStatus().isCreated
      .expectBody()
      .jsonPath("$.action").isEqualTo("CREATED")
  }

  @Suppress("LongParameterList")
  private fun createSyncRequest(
    caseloadId: String,
    timestamp: LocalDateTime,
    offenderDisplayId: String,
    offenderAccountCode: Int,
    offenderSubAccountType: String,
    offenderPostingType: String,
    amount: BigDecimal,
    transactionType: String,
    glCode: Int,
    glPostingType: String,
  ): SyncOffenderTransactionRequest {
    val transactionId = Random.nextLong(10000, 99999)
    val requestId = UUID.randomUUID()

    val offenderEntry = GeneralLedgerEntry(
      entrySequence = 1,
      code = offenderAccountCode,
      postingType = offenderPostingType,
      amount = amount,
    )

    val glEntry = GeneralLedgerEntry(
      entrySequence = 2,
      code = glCode,
      postingType = glPostingType,
      amount = amount,
    )

    return SyncOffenderTransactionRequest(
      transactionId = transactionId,
      caseloadId = caseloadId,
      transactionTimestamp = timestamp,
      createdAt = timestamp.plusSeconds(1),
      createdBy = "OMS_OWNER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 5306470,
          offenderDisplayId = offenderDisplayId,
          offenderBookingId = 2970777,
          subAccountType = offenderSubAccountType,
          postingType = offenderPostingType,
          type = transactionType,
          description = "Test Transaction for Balance Check",
          amount = amount,
          reference = "REF-$transactionId",
          generalLedgerEntries = listOf(offenderEntry, glEntry),
        ),
      ),
      requestId = requestId,
      createdByDisplayName = "OMS_OWNER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )
  }
}
