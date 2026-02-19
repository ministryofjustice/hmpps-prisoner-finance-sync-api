package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.sync

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniquePrisonNumber
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class SyncTIROffenderTransactionWithNoGeneralLedgerEntries : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should correctly fix TIR offender transaction by generating GL entries and updating all sub-accounts`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonNumber = uniquePrisonNumber()

    val regAccountCode = 2101
    val spendsAccountCode = 2102
    val savAccountCode = 2103

    val regAmount = BigDecimal("50.00")
    val savAmount = BigDecimal("0.00")
    val spndAmount = BigDecimal("5.00")

    val offenderId: Long = 2613549L
    val offenderBookingId: Long = 1231110L
    val transactionDate = LocalDateTime.of(2025, 10, 14, 0, 0, 0)

    val syncRequest = SyncOffenderTransactionRequest(
      transactionId = 465367970,
      requestId = UUID.randomUUID(),
      caseloadId = prisonId,
      transactionTimestamp = transactionDate,
      createdAt = transactionDate.withNano(562000000),
      createdBy = "SOMEONE",
      createdByDisplayName = "SOMEONE",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1, offenderId = offenderId, offenderDisplayId = prisonNumber, offenderBookingId = offenderBookingId,
          subAccountType = "REG", postingType = "CR", type = "TIR", description = "Transfer In Regular from LEI", amount = regAmount, reference = null,
          generalLedgerEntries = emptyList(),
        ),
        OffenderTransaction(
          entrySequence = 2, offenderId = offenderId, offenderDisplayId = prisonNumber, offenderBookingId = offenderBookingId,
          subAccountType = "SAV", postingType = "CR", type = "TIR", description = "Transfer In Regular from LEI", amount = savAmount, reference = null,
          generalLedgerEntries = emptyList(),
        ),
        OffenderTransaction(
          entrySequence = 3, offenderId = offenderId, offenderDisplayId = prisonNumber, offenderBookingId = offenderBookingId,
          subAccountType = "SPND", postingType = "CR", type = "TIR", description = "Transfer In Regular from LEI", amount = spndAmount, reference = null,
          generalLedgerEntries = emptyList(),
        ),
      ),
      lastModifiedAt = null, lastModifiedBy = null, lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(syncRequest))
      .exchange()
      .expectStatus().isCreated

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, regAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(regAmount)

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, spendsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(spndAmount)

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, savAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(savAmount)
  }
}
