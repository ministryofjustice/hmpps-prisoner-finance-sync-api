package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.merge

import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.domainevents.AdditionalInformation
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.domainevents.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.domainevents.DomainEventSubscriber
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

class PrisonerAccountMergeTest : SqsIntegrationTestBase() {

  private val spendsAccountCode = 2102
  private val earningsAccountCode = 1501
  private val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()

  @Test
  fun `should correctly calculate prisoner balances after merging two accounts`() {
    // Generate two distinct prisoner numbers
    val toPrisoner = UUID.randomUUID().toString().substring(0, 8).uppercase()
    val fromPrisoner = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val amount1 = BigDecimal("3.50")
    val transactionRequest1 = createSyncRequest(
      offenderDisplayId = toPrisoner,
      timestamp = LocalDateTime.now(),
      amount = amount1,
    )
    postSyncTransaction(transactionRequest1)

    // Verify initial balance for prisoner1
    verifyBalance(toPrisoner, amount1)

    // Transaction 2 for prisoner2
    val amount2 = BigDecimal("1.50")
    val transactionRequest2 = createSyncRequest(
      offenderDisplayId = fromPrisoner,
      timestamp = LocalDateTime.now().minusMinutes(5),
      amount = amount2,
    )
    postSyncTransaction(transactionRequest2)

    // Verify initial balance for prisoner2
    verifyBalance(fromPrisoner, amount2)

    // Expected final balance is the sum of the two transactions
    val expectedTotalBalance = amount1.add(amount2) // 3.50 + 1.50 = 5.00

    domainEventsTopicSnsClient.publish(
      PublishRequest.builder()
        .topicArn(domainEventsTopicArn)
        .message(
          jsonString(
            HmppsDomainEvent(
              eventType = DomainEventSubscriber.PRISONER_MERGE_EVENT_TYPE,
              additionalInformation = AdditionalInformation(
                nomsNumber = toPrisoner,
                removedNomsNumber = fromPrisoner,
                reason = "MERGE",
              ),
            ),
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(DomainEventSubscriber.PRISONER_MERGE_EVENT_TYPE).build(),
          ),
        )
        .build(),
    )

    await()
      .atMost(Duration.ofSeconds(5))
      .pollInterval(Duration.ofMillis(100))
      .untilAsserted {
        // Check Survivor has full balance
        verifyBalance(toPrisoner, expectedTotalBalance)
      }

    // Check Removed Prisoner is zeroed out
    verifyBalance(fromPrisoner, BigDecimal("0.00"))
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
