package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.integration.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration.PrisonerBalancesSyncRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class MultiPrisonBalanceAggregationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should correctly aggregate initial balances from two different prisons and handle a single-prison account balance`() {
    val prisonNumber = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val prisonA = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonB = UUID.randomUUID().toString().substring(0, 3).uppercase()

    val spendsAccountCode = 2102
    val savingsAccountCode = 2103

    val prisonASpendsBalance = BigDecimal("123.45")
    val prisonBSpendsBalance = BigDecimal("75.20")
    val prisonASpendsHoldBalance = BigDecimal("5.00")
    val prisonBSpendsHoldBalance = BigDecimal("10.00")

    val prisonBSavingsBalance = BigDecimal("200.50")
    val prisonBSavingsHoldBalance = BigDecimal("0.00")

    val migrateTimestamp = LocalDateTime.of(2025, 9, 25, 12, 0, 0)

    val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonA,
          accountCode = spendsAccountCode,
          balance = prisonASpendsBalance,
          holdBalance = prisonASpendsHoldBalance,
          asOfTimestamp = migrateTimestamp,
          transactionId = 1234L,
        ),
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonB,
          accountCode = spendsAccountCode,
          balance = prisonBSpendsBalance,
          holdBalance = prisonBSpendsHoldBalance,
          asOfTimestamp = migrateTimestamp,
          transactionId = 1234L,
        ),
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonB,
          accountCode = savingsAccountCode,
          balance = prisonBSavingsBalance,
          holdBalance = prisonBSavingsHoldBalance,
          asOfTimestamp = migrateTimestamp,
          transactionId = 1234L,
        ),
      ),
    )

    val expectedTotalSpendsBalance = prisonASpendsBalance.add(prisonBSpendsBalance)
    val expectedTotalSpendsHoldBalance = prisonASpendsHoldBalance.add(prisonBSpendsHoldBalance)

    val expectedTotalSavingsBalance = prisonBSavingsBalance
    val expectedTotalSavingsHoldBalance = prisonBSavingsHoldBalance

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
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, spendsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(expectedTotalSpendsBalance.toDouble())
      .jsonPath("$.holdBalance").isEqualTo(expectedTotalSpendsHoldBalance.toDouble())

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, savingsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(expectedTotalSavingsBalance.toDouble())
      .jsonPath("$.holdBalance").isEqualTo(expectedTotalSavingsHoldBalance.toDouble())
  }
}
