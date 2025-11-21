package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.integration.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration.GeneralLedgerPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

class InitialBalanceAggregationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should correctly aggregate initial balance and a new transaction in the general ledger`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonNumber = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val offenderSpendsAccountCode = 2102
    val prisonSpendsGLAccountCode = 2102
    val prisonBankGLAccountCode = 1501

    val initialEarningsBalance = BigDecimal("100.00")
    val initialPrisonerBalance = BigDecimal("1000.00")
    val transactionAmount = BigDecimal("50.00")

    val migrateTimestamp = LocalDateTime.of(2025, 6, 1, 23, 59, 59)
    val transactionTimestamp = LocalDateTime.of(2025, 6, 2, 0, 8, 17)

    val glMigrationRequestBody = GeneralLedgerBalancesSyncRequest(
      accountBalances = listOf(
        GeneralLedgerPointInTimeBalance(
          accountCode = prisonBankGLAccountCode,
          balance = initialEarningsBalance,
          asOfTimestamp = migrateTimestamp,
        ),
        GeneralLedgerPointInTimeBalance(
          accountCode = prisonSpendsGLAccountCode,
          balance = initialPrisonerBalance,
          asOfTimestamp = migrateTimestamp,
        ),
      ),
    )

    webTestClient
      .post()
      .uri("/migrate/general-ledger-balances/{prisonId}", prisonId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(glMigrationRequestBody))
      .exchange()
      .expectStatus().isOk

    val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonId,
          accountCode = offenderSpendsAccountCode,
          balance = initialPrisonerBalance,
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = migrateTimestamp,
          transactionId = 1234L,
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

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, offenderSpendsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(initialPrisonerBalance.toDouble())

    val offenderTransactionRequest = SyncOffenderTransactionRequest(
      transactionId = Random.Default.nextLong(),
      caseloadId = prisonId,
      transactionTimestamp = transactionTimestamp,
      createdAt = transactionTimestamp,
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
          description = "Offender Payroll",
          amount = transactionAmount.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(
              entrySequence = 1,
              code = prisonBankGLAccountCode,
              postingType = "DR",
              amount = transactionAmount.toDouble(),
            ),
            GeneralLedgerEntry(
              entrySequence = 2,
              code = offenderSpendsAccountCode,
              postingType = "CR",
              amount = transactionAmount.toDouble(),
            ),
          ),
        ),
      ),
      requestId = UUID.randomUUID(),
      createdByDisplayName = "OMS_OWNER3",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(objectMapper.writeValueAsString(offenderTransactionRequest))
      .exchange()
      .expectStatus().isCreated

    val expectedGLBalance = initialPrisonerBalance.add(transactionAmount)
    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}", prisonId, prisonSpendsGLAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(expectedGLBalance.toDouble())

    val expectedPrisonerBalance = initialPrisonerBalance.add(transactionAmount)
    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, offenderSpendsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(expectedPrisonerBalance.toDouble())
  }
}
