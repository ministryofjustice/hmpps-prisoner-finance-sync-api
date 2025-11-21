package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.holds

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

class SyncOffenderHoldRemovalTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should correctly remove a hold and update balances accordingly`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonNumber = UUID.randomUUID().toString().substring(0, 8).uppercase()
    val offenderAccountCode = 2101
    val prisonBankGLAccountCode = 1104
    val prisonCashGLAccountCode = 2101
    val holdGLAccountCode = 2199

    val initialCreditAmount = BigDecimal("50.00")
    val holdAmount = BigDecimal("10.00")
    val removalAmount = BigDecimal("10.00")

    val initialBalanceRequest = SyncOffenderTransactionRequest(
      transactionId = Random.nextLong(),
      caseloadId = prisonId,
      transactionTimestamp = LocalDateTime.of(2025, 4, 23, 8, 35, 25),
      createdAt = LocalDateTime.of(2025, 4, 23, 8, 35, 25, 99000000),
      createdBy = "SOME_USER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 2609628,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 1227181,
          subAccountType = "REG",
          postingType = "CR",
          type = "POST",
          description = "Money Through Post",
          amount = initialCreditAmount.toDouble(),
          reference = "GRAN",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = prisonBankGLAccountCode, postingType = "DR", amount = initialCreditAmount.toDouble()),
            GeneralLedgerEntry(entrySequence = 2, code = prisonCashGLAccountCode, postingType = "CR", amount = initialCreditAmount.toDouble()),
          ),
        ),
      ),
      requestId = UUID.randomUUID(),
      createdByDisplayName = "SOME_USER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(initialBalanceRequest))
      .exchange()
      .expectStatus().isCreated

    val addHoldRequest = SyncOffenderTransactionRequest(
      transactionId = Random.nextLong(),
      caseloadId = prisonId,
      transactionTimestamp = LocalDateTime.of(2025, 9, 18, 16, 57, 11),
      createdAt = LocalDateTime.of(2025, 9, 18, 16, 57, 11, 971000000),
      createdBy = "SOME_USER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 2609628,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 1227181,
          subAccountType = "REG",
          postingType = "DR",
          type = "HOA",
          description = "HOLD",
          amount = holdAmount.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = prisonCashGLAccountCode, postingType = "DR", amount = holdAmount.toDouble()),
            GeneralLedgerEntry(entrySequence = 2, code = holdGLAccountCode, postingType = "CR", amount = holdAmount.toDouble()),
          ),
        ),
      ),
      requestId = UUID.randomUUID(),
      createdByDisplayName = "SOME_USER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(addHoldRequest))
      .exchange()
      .expectStatus().isCreated

    val removeHoldRequest = SyncOffenderTransactionRequest(
      transactionId = Random.nextLong(),
      caseloadId = prisonId,
      transactionTimestamp = LocalDateTime.of(2025, 9, 19, 10, 0, 0),
      createdAt = LocalDateTime.of(2025, 9, 19, 10, 0, 0, 0),
      createdBy = "SOME_USER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 2609628,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 1227181,
          subAccountType = "REG",
          postingType = "CR",
          type = "HOR",
          description = "HOLD RELEASE",
          amount = removalAmount.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = holdGLAccountCode, postingType = "DR", amount = removalAmount.toDouble()),
            GeneralLedgerEntry(entrySequence = 2, code = prisonCashGLAccountCode, postingType = "CR", amount = removalAmount.toDouble()),
          ),
        ),
      ),
      requestId = UUID.randomUUID(),
      createdByDisplayName = "SOME_USER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(removeHoldRequest))
      .exchange()
      .expectStatus().isCreated

    val expectedOffenderFinalBalance = initialCreditAmount
    val expectedOffenderFinalHoldBalance = BigDecimal.ZERO

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, offenderAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(expectedOffenderFinalBalance.toDouble())
      .jsonPath("$.holdBalance").isEqualTo(expectedOffenderFinalHoldBalance.toDouble())

    // Verify the GL accounts are updated correctly after hold removal
    val expectedGLCashBalance = initialCreditAmount
    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}", prisonId, prisonCashGLAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(expectedGLCashBalance.toDouble())

    val expectedGLHoldBalance = BigDecimal.ZERO
    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}", prisonId, holdGLAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(expectedGLHoldBalance.toDouble())
  }
}
