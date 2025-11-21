package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.integration.sync

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.SyncOffenderTransactionRequest
import java.time.LocalDateTime
import java.util.UUID

class SyncOffenderTransactionWithNoGeneralLedgerEntries : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should correctly skip offender transaction with type OT and no GL entries`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonNumber = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val cashAccountCode: Int = 2101
    val spendsAccountCode: Int = 2102
    val txnAmount = 10.00

    val offenderId: Long = 2605754L
    val offenderBookingId: Long = 1223356L
    val transactionDate = LocalDateTime.of(2024, 12, 6, 15, 59, 55)

    val syncRequest = SyncOffenderTransactionRequest(
      transactionId = 451524236, requestId = UUID.randomUUID(), caseloadId = prisonId,
      transactionTimestamp = transactionDate, createdAt = transactionDate.withNano(728000000), createdBy = "JWOOLCOCK_GEN",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1, offenderId = offenderId, offenderDisplayId = prisonNumber, offenderBookingId = offenderBookingId,
          subAccountType = "REG", postingType = "DR", type = "OT", description = "Sub-Account Transfer DR REG", amount = txnAmount, reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = cashAccountCode, postingType = "DR", amount = txnAmount),
            GeneralLedgerEntry(entrySequence = 2, code = spendsAccountCode, postingType = "CR", amount = txnAmount),
          ),
        ),
        OffenderTransaction(
          entrySequence = 2, offenderId = offenderId, offenderDisplayId = prisonNumber, offenderBookingId = offenderBookingId,
          subAccountType = "SPND", postingType = "CR", type = "OT", description = "Sub-Account Transfer CR SPND", amount = txnAmount, reference = null,
          generalLedgerEntries = emptyList(),
        ),
      ),
      createdByDisplayName = "SOME_ONE", lastModifiedAt = null, lastModifiedBy = null, lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(syncRequest))
      .exchange()
      .expectStatus().isCreated

    val expectedRegBalance = -txnAmount
    val expectedSpndBalance = txnAmount

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, cashAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(expectedRegBalance)

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, spendsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(expectedSpndBalance)
  }
}
