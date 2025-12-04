package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.merge

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

class PrisonerAccountMergeTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val spendsAccountCode = 2102
  private val earningsAccountCode = 1501
  private val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()

  @Test
  fun `should correctly calculate prisoner balances after merging two accounts`() {
    // Generate two distinct prisoner numbers
    val prisoner1 = UUID.randomUUID().toString().substring(0, 8).uppercase()
    val prisoner2 = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val amount1 = BigDecimal("3.50")
    val transactionRequest1 = createSyncRequest(
      offenderDisplayId = prisoner1,
      timestamp = LocalDateTime.now(),
      amount = amount1,
    )
    postSyncTransaction(transactionRequest1)

    // Verify initial balance for prisoner1
    verifyBalance(prisoner1, amount1)

    // Transaction 2 for prisoner2
    val amount2 = BigDecimal("1.50")
    val transactionRequest2 = createSyncRequest(
      offenderDisplayId = prisoner2,
      timestamp = LocalDateTime.now().minusMinutes(5),
      amount = amount2,
    )
    postSyncTransaction(transactionRequest2)

    // Verify initial balance for prisoner2
    verifyBalance(prisoner2, amount2)

    // Expected final balance is the sum of the two transactions
    val expectedTotalBalance = amount1.add(amount2) // 3.50 + 1.50 = 5.00

    // TODO: Call the account merge function here (e.g., merge prisoner1 into prisoner2)

    // Verify initial balance for prisoner2
    verifyBalance(prisoner2, expectedTotalBalance)

    // TODO: Should we verify the final balance of prisoner1 is 0 (or do we check for 1 404 Not Found
  }

  private fun verifyBalance(prisonNumber: String, expectedAmount: BigDecimal) {
    webTestClient
      .get()
      .uri("/reconcile/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items.length()").isEqualTo(1)
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $spendsAccountCode)].totalBalance").isEqualTo(expectedAmount.toDouble())
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $spendsAccountCode)].holdBalance").isEqualTo(0)
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

  private fun createSyncRequest(
    offenderDisplayId: String,
    timestamp: LocalDateTime,
    amount: BigDecimal,
    offenderAccountCode: Int = spendsAccountCode,
    offenderSubAccountType: String = "SPND",
    offenderPostingType: String = "CR",
    transactionType: String = "A_EARN",
    glCode: Int = earningsAccountCode,
    glPostingType: String = "DR",
  ): SyncOffenderTransactionRequest {
    val transactionId = Random.nextLong(10000, 99999)
    val requestId = UUID.randomUUID()

    val glEntry = GeneralLedgerEntry(
      entrySequence = 1,
      code = glCode,
      postingType = glPostingType,
      amount = amount.toDouble(),
    )
    val offenderEntry = GeneralLedgerEntry(
      entrySequence = 2,
      code = offenderAccountCode,
      postingType = offenderPostingType,
      amount = amount.toDouble(),
    )

    return SyncOffenderTransactionRequest(
      transactionId = transactionId,
      caseloadId = prisonId,
      transactionTimestamp = timestamp,
      createdAt = timestamp.plusSeconds(1),
      createdBy = "OMS_OWNER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = Random.nextLong(1000000, 9999999),
          offenderDisplayId = offenderDisplayId,
          offenderBookingId = Random.nextLong(1000000, 9999999),
          subAccountType = offenderSubAccountType,
          postingType = offenderPostingType,
          type = transactionType,
          description = "Offender Payroll",
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
