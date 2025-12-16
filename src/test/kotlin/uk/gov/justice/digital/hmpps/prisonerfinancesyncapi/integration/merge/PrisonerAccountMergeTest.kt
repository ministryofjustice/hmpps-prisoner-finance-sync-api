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
  fun `should correctly calculate prisoner balances after merging two accounts (Standard Transactions)`() {
    val toPrisoner = UUID.randomUUID().toString().substring(0, 8).uppercase()
    val fromPrisoner = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val amount1 = BigDecimal("3.50")
    postSyncTransaction(createSyncRequest(toPrisoner, LocalDateTime.now(), amount1))
    verifyBalance(toPrisoner, amount1)

    val amount2 = BigDecimal("1.50")
    postSyncTransaction(createSyncRequest(fromPrisoner, LocalDateTime.now().minusMinutes(5), amount2))
    verifyBalance(fromPrisoner, amount2)

    val expectedTotalBalance = amount1.add(amount2)

    publishMergeEvent(toPrisoner, fromPrisoner)

    await().atMost(Duration.ofSeconds(5)).untilAsserted {
      verifyBalance(toPrisoner, expectedTotalBalance)
    }
    verifyBalance(fromPrisoner, BigDecimal.ZERO)
  }

  @Test
  fun `should correctly merge accounts when removed account has migrated initial balances`() {
    val toPrisoner = UUID.randomUUID().toString().substring(0, 8).uppercase()
    val fromPrisoner = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val migrationAmount = BigDecimal("50.00")
    migrateBalance(fromPrisoner, spendsAccountCode, migrationAmount, LocalDateTime.now().minusDays(1))
    verifyBalance(fromPrisoner, migrationAmount)

    val existingAmount = BigDecimal("10.00")

    postSyncTransaction(createSyncRequest(toPrisoner, LocalDateTime.now(), existingAmount))
    verifyBalance(toPrisoner, existingAmount)

    val expectedTotalBalance = migrationAmount.add(existingAmount)

    publishMergeEvent(toPrisoner, fromPrisoner)

    await().atMost(Duration.ofSeconds(5)).untilAsserted {
      verifyBalance(toPrisoner, expectedTotalBalance)
    }
    verifyBalance(fromPrisoner, BigDecimal.ZERO)
  }

  @Test
  fun `should correctly merge accounts when surving account has migrated initial balances`() {
    val toPrisoner = UUID.randomUUID().toString().substring(0, 8).uppercase()
    val fromPrisoner = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val migrationAmount = BigDecimal("50.00")
    migrateBalance(toPrisoner, spendsAccountCode, migrationAmount, LocalDateTime.now().minusDays(1))
    verifyBalance(toPrisoner, migrationAmount)

    val existingAmount = BigDecimal("10.00")
    postSyncTransaction(createSyncRequest(fromPrisoner, LocalDateTime.now(), existingAmount))
    verifyBalance(fromPrisoner, existingAmount)

    val expectedTotalBalance = migrationAmount.add(existingAmount)

    publishMergeEvent(toPrisoner, fromPrisoner)

    await().atMost(Duration.ofSeconds(5)).untilAsserted {
      verifyBalance(toPrisoner, expectedTotalBalance)
    }
    verifyBalance(fromPrisoner, BigDecimal.ZERO)
  }

  private fun publishMergeEvent(toPrisoner: String, fromPrisoner: String) {
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
          mapOf("eventType" to MessageAttributeValue.builder().dataType("String").stringValue(DomainEventSubscriber.PRISONER_MERGE_EVENT_TYPE).build()),
        )
        .build(),
    )
  }

  private fun verifyBalance(prisonNumber: String, expectedAmount: BigDecimal) {
    webTestClient
      .get()
      .uri("/reconcile/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[?(@.accountCode == $spendsAccountCode)].totalBalance")
      .value<List<Double>> { balances ->
        val total = balances.sum()
        if (BigDecimal.valueOf(total).compareTo(expectedAmount) != 0) {
          throw AssertionError("Expected $expectedAmount but got $total. Accounts found: $balances")
        }
      }
  }

  private fun migrateBalance(
    prisonNumber: String,
    accountCode: Int,
    amount: BigDecimal,
    timestamp: LocalDateTime = LocalDateTime.now(),
  ) {
    val migrationRequest = uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance(
          prisonId = prisonId,
          accountCode = accountCode,
          balance = amount,
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = timestamp,
          transactionId = Random.nextLong(100000, 999999),
        ),
      ),
    )

    webTestClient
      .post()
      .uri("/migrate/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(migrationRequest))
      .exchange()
      .expectStatus().isOk
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

    val glEntry = GeneralLedgerEntry(1, glCode, glPostingType, amount.toDouble())
    val offenderEntry = GeneralLedgerEntry(2, offenderAccountCode, offenderPostingType, amount.toDouble())

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
