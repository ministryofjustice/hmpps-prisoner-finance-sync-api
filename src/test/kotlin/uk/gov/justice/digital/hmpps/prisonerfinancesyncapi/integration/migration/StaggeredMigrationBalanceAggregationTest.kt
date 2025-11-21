package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

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
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.MIGRATION_CLEARING_ACCOUNT
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

class StaggeredMigrationBalanceAggregationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val spendsAccountCode = 2102

  private val oldestMigrationTime = LocalDateTime.of(2024, 1, 1, 10, 0, 0)
  private val middleTransactionTime = LocalDateTime.of(2024, 2, 1, 10, 0, 0)
  private val latestMigrationTime = LocalDateTime.of(2024, 3, 1, 10, 0, 0)

  @Test
  fun `should correctly aggregate balances by summing per-prison balances when migration dates differ`() {
    val prisonNumber = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val prisonA = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonB = UUID.randomUUID().toString().substring(0, 3).uppercase()

    val initialBalanceA = BigDecimal("50.00")
    val initialBalanceB = BigDecimal("100.00")

    val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonA,
          accountCode = spendsAccountCode,
          balance = initialBalanceA,
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = oldestMigrationTime,
          transactionId = 1000L,
        ),
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonB,
          accountCode = spendsAccountCode,
          balance = initialBalanceB,
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = latestMigrationTime,
          transactionId = 1001L,
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

    val transactionBetweenMigrations = BigDecimal("10.00")
    val transactionTimeBetween = middleTransactionTime

    val betweenTransactionRequest = createSyncRequest(
      caseloadId = prisonA,
      timestamp = transactionTimeBetween,
      offenderDisplayId = prisonNumber,
      offenderAccountCode = spendsAccountCode,
      offenderSubAccountType = "SPND",
      offenderPostingType = "CR",
      amount = transactionBetweenMigrations,
      transactionType = "A_EARN",
      glCode = MIGRATION_CLEARING_ACCOUNT,
      glPostingType = "DR",
    )
    postSyncTransaction(betweenTransactionRequest)

    val finalBalanceAV2 = initialBalanceA.add(transactionBetweenMigrations)

    val finalBalanceBV2 = initialBalanceB

    val expectedAggregatedBalanceV2 = finalBalanceAV2.add(finalBalanceBV2)

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, spendsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(expectedAggregatedBalanceV2.toDouble())
      .jsonPath("$.holdBalance").isEqualTo(0)
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
      amount = amount.toDouble(),
    )

    val glEntry = GeneralLedgerEntry(
      entrySequence = 2,
      code = glCode,
      postingType = glPostingType,
      amount = amount.toDouble(),
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
          description = if (transactionType == "HOA") "HOLD ALLOC" else "Test Transaction for Aggregation",
          amount = amount.toDouble(),
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
