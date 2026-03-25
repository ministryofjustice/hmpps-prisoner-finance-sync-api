package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.sync

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniquePrisonNumber
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.utils.isSumMoneyEqual
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

class SyncOffenderTransactionBalanceIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `given a single offender transaction is synced, the offender and prison account balances should be updated correctly`() {
    val prisonNumber = uniquePrisonNumber()

    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val offenderAccountCode = 2102
    val prisonAccountCode = 1501

    val transactionRequest = createSingleOffenderTransaction(prisonNumber, prisonId)

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(transactionRequest)
      .exchange()
      .expectStatus().isCreated
      .expectBody()
      .jsonPath("$.action").isEqualTo("CREATED")

    webTestClient
      .get()
      .uri("/reconcile/prisoner-balances/$prisonNumber")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[?(@.accountCode == $offenderAccountCode)].totalBalance").isSumMoneyEqual(BigDecimal("2.95"))

    webTestClient
      .get()
      .uri("/reconcile/general-ledger-balances/$prisonId")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[?(@.accountCode == $prisonAccountCode)].balance").isSumMoneyEqual(BigDecimal("2.95"))
  }

  @Test
  fun `given a transaction affecting multiple offenders is synced, all balances should be updated correctly`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val offender1DisplayId = "A0148EC"
    val offender2DisplayId = "A0153VD"
    val offenderAccountCode = 2102
    val prisonAccountCode = 2501

    val transactionRequest = createCanteenSpendTransaction(prisonId, offender1DisplayId, offender2DisplayId)

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(transactionRequest)
      .exchange()
      .expectStatus().isCreated
      .expectBody()
      .jsonPath("$.action").isEqualTo("CREATED")

    webTestClient
      .get()
      .uri("/reconcile/prisoner-balances/$offender1DisplayId")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[?(@.accountCode == $offenderAccountCode)].totalBalance").isSumMoneyEqual(BigDecimal("-1.40"))

    webTestClient
      .get()
      .uri("/reconcile/prisoner-balances/$offender2DisplayId")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[?(@.accountCode == $offenderAccountCode)].totalBalance").isSumMoneyEqual(BigDecimal("-2.20"))

    webTestClient
      .get()
      .uri("/reconcile/general-ledger-balances/$prisonId")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[?(@.accountCode == $prisonAccountCode)].balance").isSumMoneyEqual(BigDecimal("3.60"))
  }

  private fun createSingleOffenderTransaction(prisonNumber: String, caseloadId: String): SyncOffenderTransactionRequest = SyncOffenderTransactionRequest(
    transactionId = Random.nextLong(),
    caseloadId = caseloadId,
    transactionTimestamp = LocalDateTime.of(2025, 6, 2, 0, 8, 17),
    createdAt = LocalDateTime.of(2025, 6, 2, 0, 8, 17, 830000000),
    createdBy = "OMS_OWNER",
    offenderTransactions = listOf(
      OffenderTransaction(
        entrySequence = 1,
        offenderId = 5306470,
        offenderDisplayId = prisonNumber,
        offenderBookingId = 2970777,
        subAccountType = "SPND",
        postingType = "CR",
        type = "A_EARN",
        description = "Offender Payroll From:01/06/2025 To:01/06/2025",
        amount = BigDecimal("2.95"),
        reference = null,
        generalLedgerEntries = listOf(
          GeneralLedgerEntry(entrySequence = 1, code = 1501, postingType = "DR", amount = BigDecimal("2.95")),
          GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = BigDecimal("2.95")),
        ),
      ),
    ),
    requestId = UUID.randomUUID(),
    createdByDisplayName = "OMS_OWNER3",
    lastModifiedAt = null,
    lastModifiedBy = null,
    lastModifiedByDisplayName = null,
  )

  private fun createCanteenSpendTransaction(caseloadId: String, offender1DisplayId: String, offender2DisplayId: String): SyncOffenderTransactionRequest = SyncOffenderTransactionRequest(
    transactionId = 451524232,
    requestId = UUID.fromString("c1b4c3d4-e5f6-7890-1234-567890abcdef"),
    caseloadId = caseloadId,
    transactionTimestamp = LocalDateTime.of(2024, 12, 6, 15, 30, 4),
    createdAt = LocalDateTime.of(2024, 12, 6, 15, 30, 4),
    createdBy = "OMS_OWNER",
    createdByDisplayName = "Jeffrey",
    offenderTransactions = listOf(
      OffenderTransaction(
        entrySequence = 1,
        offenderId = 2605754,
        offenderDisplayId = offender1DisplayId,
        offenderBookingId = 1223356,
        subAccountType = "SPND",
        postingType = "DR",
        type = "CANT",
        description = "Canteen Spend",
        amount = BigDecimal("1.40"),
        reference = null,
        generalLedgerEntries = listOf(
          GeneralLedgerEntry(entrySequence = 1, code = 2102, postingType = "DR", amount = BigDecimal("1.40")),
          GeneralLedgerEntry(entrySequence = 2, code = 2501, postingType = "CR", amount = BigDecimal("1.40")),
        ),
      ),
      OffenderTransaction(
        entrySequence = 2,
        offenderId = 4305755,
        offenderDisplayId = offender2DisplayId,
        offenderBookingId = 789567,
        subAccountType = "SPND",
        postingType = "DR",
        type = "CANT",
        description = "Canteen Spend",
        amount = BigDecimal("2.20"),
        reference = null,
        generalLedgerEntries = listOf(
          GeneralLedgerEntry(entrySequence = 1, code = 2102, postingType = "DR", amount = BigDecimal("2.20")),
          GeneralLedgerEntry(entrySequence = 2, code = 2501, postingType = "CR", amount = BigDecimal("2.20")),
        ),
      ),
    ),
    lastModifiedAt = null,
    lastModifiedBy = null,
    lastModifiedByDisplayName = null,
  )
}
