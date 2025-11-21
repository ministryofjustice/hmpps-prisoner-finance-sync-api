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

class MigratePrisonerBalancesTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should migrate initial balances for a new prisoner and retrieve them correctly`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonNumber = UUID.randomUUID().toString().substring(0, 8).uppercase()

    val cashAccountCode = 2101
    val cashBalance = BigDecimal("0")
    val spendsAccountCode = 2102
    val spendsBalance = BigDecimal("50.00")
    val savingsAccountCode = 2103
    val savingsBalance = BigDecimal("123.45")

    val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(prisonId = prisonId, accountCode = cashAccountCode, balance = cashBalance, holdBalance = BigDecimal.ZERO, asOfTimestamp = LocalDateTime.now(), transactionId = 1234L),
        PrisonerAccountPointInTimeBalance(prisonId = prisonId, accountCode = spendsAccountCode, balance = spendsBalance, holdBalance = BigDecimal.ZERO, asOfTimestamp = LocalDateTime.now(), transactionId = 1234L),
        PrisonerAccountPointInTimeBalance(prisonId = prisonId, accountCode = savingsAccountCode, balance = savingsBalance, holdBalance = BigDecimal.ZERO, asOfTimestamp = LocalDateTime.now(), transactionId = 1234L),
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
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, cashAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(cashBalance.toDouble())
      .jsonPath("$.code").isEqualTo(cashAccountCode)
      .jsonPath("$.name").isEqualTo("Cash")
      .jsonPath("$.holdBalance").isEqualTo(BigDecimal.ZERO.toDouble())

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, spendsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(spendsBalance.toDouble())
      .jsonPath("$.code").isEqualTo(spendsAccountCode)
      .jsonPath("$.name").isEqualTo("Spends")
      .jsonPath("$.holdBalance").isEqualTo(BigDecimal.ZERO.toDouble())

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, savingsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(savingsBalance.toDouble())
      .jsonPath("$.code").isEqualTo(savingsAccountCode)
      .jsonPath("$.name").isEqualTo("Savings")
      .jsonPath("$.holdBalance").isEqualTo(BigDecimal.ZERO.toDouble())

    webTestClient
      .get()
      .uri("/reconcile/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items.length()").isEqualTo(3)
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $cashAccountCode)].totalBalance").isEqualTo(cashBalance.toDouble())
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $cashAccountCode)].holdBalance").isEqualTo(0)
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $spendsAccountCode)].totalBalance").isEqualTo(spendsBalance.toDouble())
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $spendsAccountCode)].holdBalance").isEqualTo(0)
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $savingsAccountCode)].totalBalance").isEqualTo(savingsBalance.toDouble())
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $savingsAccountCode)].holdBalance").isEqualTo(0)

    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}", prisonId, spendsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(0)
      .jsonPath("$.name").isEqualTo("Spends")

    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}", prisonId, savingsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isEqualTo(0)
      .jsonPath("$.name").isEqualTo("Savings")
  }
}
