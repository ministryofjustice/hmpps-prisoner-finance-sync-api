package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.sync

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.utils.isMoneyEqual
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

class PrisonerGeneralLedgerAccountsAggregationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private fun generateRandomCode(length: Int): String = UUID.randomUUID().toString().substring(0, length).uppercase()

  @Test
  fun `should sum transactions from multiple prisoners in a single general ledger account`() {
    val prisonId = generateRandomCode(3)
    val offender1DisplayId = generateRandomCode(7)
    val offender2DisplayId = generateRandomCode(7)

    val offenderAccountCode = 2102
    val prisonAccountCode = 2102
    val nonPrisonerGLAccountCode = 1501

    val transactionAmount1 = BigDecimal("5.50")
    val transactionAmount2 = BigDecimal("8.75")

    val totalExpectedBalance = transactionAmount1.add(transactionAmount2)

    val transactionRequest1 = SyncOffenderTransactionRequest(
      transactionId = Random.nextLong(),
      caseloadId = prisonId,
      transactionTimestamp = LocalDateTime.of(2025, 6, 2, 0, 8, 17),
      createdAt = LocalDateTime.of(2025, 6, 2, 0, 8, 17),
      createdBy = "OMS_OWNER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 5306470,
          offenderDisplayId = offender1DisplayId,
          offenderBookingId = 2970777,
          subAccountType = "SPND",
          postingType = "CR",
          type = "A_EARN",
          description = "Offender Payroll",
          amount = transactionAmount1,
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = nonPrisonerGLAccountCode, postingType = "DR", amount = transactionAmount1),
            GeneralLedgerEntry(entrySequence = 2, code = offenderAccountCode, postingType = "CR", amount = transactionAmount1),
          ),
        ),
      ),
      requestId = UUID.randomUUID(),
      createdByDisplayName = "OMS_OWNER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(transactionRequest1))
      .exchange()
      .expectStatus().isCreated

    val transactionRequest2 = SyncOffenderTransactionRequest(
      transactionId = Random.nextLong(),
      caseloadId = prisonId,
      transactionTimestamp = LocalDateTime.of(2025, 6, 3, 0, 8, 17),
      createdAt = LocalDateTime.of(2025, 6, 3, 0, 8, 17),
      createdBy = "OMS_OWNER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 5306471,
          offenderDisplayId = offender2DisplayId,
          offenderBookingId = 2970778,
          subAccountType = "SPND",
          postingType = "CR",
          type = "A_EARN",
          description = "Offender Payroll",
          amount = transactionAmount2,
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = nonPrisonerGLAccountCode, postingType = "DR", amount = transactionAmount2),
            GeneralLedgerEntry(entrySequence = 2, code = offenderAccountCode, postingType = "CR", amount = transactionAmount2),
          ),
        ),
      ),
      requestId = UUID.randomUUID(),
      createdByDisplayName = "OMS_OWNER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(transactionRequest2))
      .exchange()
      .expectStatus().isCreated

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", offender1DisplayId, offenderAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isMoneyEqual(transactionAmount1)

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", offender2DisplayId, offenderAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isMoneyEqual(transactionAmount2)

    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}", prisonId, prisonAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isMoneyEqual(totalExpectedBalance)
  }
}
