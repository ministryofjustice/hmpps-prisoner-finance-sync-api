package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.reports

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
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class SummaryOfPaymentsAndReceiptsReportTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should generate a report with multiple payments and receipts for the same day`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonNumber = UUID.randomUUID().toString().substring(0, 8).uppercase()
    val prisonBankGLAccountCode = 1501
    val spendingGLAccountCode = 2102
    val telephoneGLAccountCode = 2502

    val dateOfTransactions = LocalDate.of(2025, 9, 22)

    val receipt1Amount = BigDecimal("2.3")
    val receipt1Request = SyncOffenderTransactionRequest(
      transactionId = 485261420,
      requestId = UUID.fromString("e3c8980c-2722-47af-bf06-dc250bd43c3d"),
      caseloadId = prisonId,
      transactionTimestamp = dateOfTransactions.atTime(9, 30, 0),
      createdAt = LocalDateTime.now().minusMinutes(5),
      createdBy = "OMS_OWNER",
      createdByDisplayName = "OMS_OWNER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 2076970,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 2805083,
          subAccountType = "SPND",
          postingType = "CR",
          type = "A_EARN",
          description = "Offender Payroll From:22/09/2025 To:22/09/2025",
          amount = receipt1Amount.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = prisonBankGLAccountCode, postingType = "DR", amount = receipt1Amount.toDouble()),
            GeneralLedgerEntry(entrySequence = 2, code = spendingGLAccountCode, postingType = "CR", amount = receipt1Amount.toDouble()),
          ),
        ),
      ),
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    val receipt2Amount = BigDecimal("5.0")
    val receipt2Request = SyncOffenderTransactionRequest(
      transactionId = 485261421,
      requestId = UUID.randomUUID(),
      caseloadId = prisonId,
      transactionTimestamp = dateOfTransactions.atTime(10, 0, 0),
      createdAt = LocalDateTime.now().minusMinutes(4),
      createdBy = "OMS_OWNER_2",
      createdByDisplayName = "OMS_OWNER_2",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 2076971,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 2805084,
          subAccountType = "SPND",
          postingType = "CR",
          type = "A_EARN",
          description = "Offender Payroll From:22/09/2025 To:22/09/2025",
          amount = receipt2Amount.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = prisonBankGLAccountCode, postingType = "DR", amount = receipt2Amount.toDouble()),
            GeneralLedgerEntry(entrySequence = 2, code = spendingGLAccountCode, postingType = "CR", amount = receipt2Amount.toDouble()),
          ),
        ),
      ),
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    val payment1Amount = BigDecimal("10.00")
    val payment1Request = SyncOffenderTransactionRequest(
      transactionId = 485439414,
      requestId = UUID.fromString("8153b344-2cfb-4d69-b42d-ca898beb1035"),
      caseloadId = prisonId,
      transactionTimestamp = dateOfTransactions.atTime(14, 0, 0),
      createdAt = LocalDateTime.now().minusMinutes(1),
      createdBy = "KQC42R",
      createdByDisplayName = "KQC42R",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 5366869,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 3010229,
          subAccountType = "SPND",
          postingType = "DR",
          type = "PHONE",
          description = "Phone Credit",
          amount = payment1Amount.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = spendingGLAccountCode, postingType = "DR", amount = payment1Amount.toDouble()),
            GeneralLedgerEntry(entrySequence = 2, code = telephoneGLAccountCode, postingType = "CR", amount = payment1Amount.toDouble()),
          ),
        ),
      ),
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    val payment2Amount = BigDecimal("5.00")
    val payment2Request = SyncOffenderTransactionRequest(
      transactionId = 485439415,
      requestId = UUID.randomUUID(),
      caseloadId = prisonId,
      transactionTimestamp = dateOfTransactions.atTime(15, 0, 0),
      createdAt = LocalDateTime.now(),
      createdBy = "KQC42R_2",
      createdByDisplayName = "KQC42R_2",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 5366870,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 3010230,
          subAccountType = "SPND",
          postingType = "DR",
          type = "PHONE",
          description = "Phone Credit",
          amount = payment2Amount.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = spendingGLAccountCode, postingType = "DR", amount = payment2Amount.toDouble()),
            GeneralLedgerEntry(entrySequence = 2, code = telephoneGLAccountCode, postingType = "CR", amount = payment2Amount.toDouble()),
          ),
        ),
      ),
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(receipt1Request))
      .exchange()
      .expectStatus().isCreated

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(receipt2Request))
      .exchange()
      .expectStatus().isCreated

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(payment1Request))
      .exchange()
      .expectStatus().isCreated

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(payment2Request))
      .exchange()
      .expectStatus().isCreated

    val totalReceipts = receipt1Amount + receipt2Amount
    val totalPayments = payment1Amount + payment2Amount

    webTestClient
      .get()
      .uri("/prisons/{prisonId}/reports/summary-of-payments-and-receipts?date={date}", prisonId, dateOfTransactions)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.postings.length()").isEqualTo(2)
      .jsonPath("$.postings[0].description").isEqualTo("Phone Credit")
      .jsonPath("$.postings[0].transactionUsage").isEqualTo("Payments")
      .jsonPath("$.postings[0].credits").isEqualTo(BigDecimal.ZERO.toDouble())
      .jsonPath("$.postings[0].debits").isEqualTo(totalPayments.toDouble())
      .jsonPath("$.postings[0].total").isEqualTo(totalPayments.toDouble())
      .jsonPath("$.postings[1].description").isEqualTo("Offender Payroll")
      .jsonPath("$.postings[1].transactionUsage").isEqualTo("Receipts")
      .jsonPath("$.postings[1].credits").isEqualTo(totalReceipts.toDouble())
      .jsonPath("$.postings[1].debits").isEqualTo(BigDecimal.ZERO.toDouble())
      .jsonPath("$.postings[1].total").isEqualTo(totalReceipts.toDouble())
  }
}
