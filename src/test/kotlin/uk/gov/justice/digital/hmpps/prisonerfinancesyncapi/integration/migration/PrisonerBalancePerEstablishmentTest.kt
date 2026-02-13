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
import java.util.UUID
import kotlin.random.Random

class PrisonerBalancePerEstablishmentTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val spendsAccountCode = 2102
  private val savingsAccountCode = 2103
  private val migrateTimestamp = LocalDateTime.of(2025, 10, 1, 12, 0, 0)

  private val holdsGlAccountCode = 2199

  @Test
  fun `should correctly calculate prisoner balances per establishment after migration and subsequent transactions`() {
    val prisonNumber = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val prisonA = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonB = UUID.randomUUID().toString().substring(0, 3).uppercase()

    val earningsAccountCode = 1501
    val canteenPayable = 2501

    val initialSpendsA = BigDecimal("10.00")
    val initialHoldA = BigDecimal("5.00")
    val initialSpendsB = BigDecimal("20.00")
    val initialHoldB = BigDecimal("0.00")
    val initialSavingsA = BigDecimal("50.00")

    val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonA,
          accountCode = spendsAccountCode,
          balance = initialSpendsA,
          holdBalance = initialHoldA,
          asOfTimestamp = migrateTimestamp,
          transactionId = 1000L,
        ),
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonB,
          accountCode = spendsAccountCode,
          balance = initialSpendsB,
          holdBalance = initialHoldB,
          asOfTimestamp = migrateTimestamp,
          transactionId = 1001L,
        ),
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonA,
          accountCode = savingsAccountCode,
          balance = initialSavingsA,
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = migrateTimestamp,
          transactionId = 1002L,
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

    val postMigrationTimestamp = migrateTimestamp.plusHours(1)

    val creditAAmount = BigDecimal("10.00")
    val transactionRequestA = createSyncRequest(
      caseloadId = prisonA,
      timestamp = postMigrationTimestamp,
      offenderDisplayId = prisonNumber,
      offenderAccountCode = spendsAccountCode,
      offenderSubAccountType = "SPND",
      offenderPostingType = "CR",
      amount = creditAAmount,
      transactionType = "A_EARN",
      glCode = earningsAccountCode,
      glPostingType = "DR",
    )
    postSyncTransaction(transactionRequestA)

    val debitBAmount = BigDecimal("5.00")
    val transactionRequestB = createSyncRequest(
      caseloadId = prisonB,
      timestamp = postMigrationTimestamp.plusMinutes(1),
      offenderDisplayId = prisonNumber,
      offenderAccountCode = spendsAccountCode,
      offenderSubAccountType = "SPND",
      offenderPostingType = "DR",
      amount = debitBAmount,
      transactionType = "CANT",
      glCode = canteenPayable,
      glPostingType = "CR",
    )
    postSyncTransaction(transactionRequestB)

    val holdAAmount = BigDecimal("2.00")
    val transactionRequestHoldA = createSyncRequest(
      caseloadId = prisonA,
      timestamp = postMigrationTimestamp.plusMinutes(2),
      offenderDisplayId = prisonNumber,
      offenderAccountCode = savingsAccountCode,
      offenderSubAccountType = "SAV",
      offenderPostingType = "DR",
      amount = holdAAmount,
      transactionType = "HOA",
      glCode = holdsGlAccountCode,
      glPostingType = "CR",
    )
    postSyncTransaction(transactionRequestHoldA)

    val expectedSpendsABalance = initialSpendsA.add(creditAAmount)
    val expectedSpendsAHold = initialHoldA

    val expectedSpendsBBalance = initialSpendsB.subtract(debitBAmount)

    val expectedSavingsABalance = initialSavingsA.subtract(holdAAmount)
    val expectedSavingsAHold = BigDecimal.ZERO.add(holdAAmount)

    webTestClient
      .get()
      .uri("/reconcile/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items.length()").isEqualTo(3)
      .jsonPath("$.items[?(@.prisonId == '$prisonA' && @.accountCode == $spendsAccountCode)].totalBalance").isMoneyEqual(expectedSpendsABalance)
      .jsonPath("$.items[?(@.prisonId == '$prisonA' && @.accountCode == $spendsAccountCode)].holdBalance").isMoneyEqual(expectedSpendsAHold)
      .jsonPath("$.items[?(@.prisonId == '$prisonB' && @.accountCode == $spendsAccountCode)].totalBalance").isMoneyEqual(expectedSpendsBBalance)
      .jsonPath("$.items[?(@.prisonId == '$prisonB' && @.accountCode == $spendsAccountCode)].holdBalance").isMoneyEqual(BigDecimal.ZERO)
      .jsonPath("$.items[?(@.prisonId == '$prisonA' && @.accountCode == $savingsAccountCode)].totalBalance").isMoneyEqual(expectedSavingsABalance)
      .jsonPath("$.items[?(@.prisonId == '$prisonA' && @.accountCode == $savingsAccountCode)].holdBalance").isMoneyEqual(expectedSavingsAHold)
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
