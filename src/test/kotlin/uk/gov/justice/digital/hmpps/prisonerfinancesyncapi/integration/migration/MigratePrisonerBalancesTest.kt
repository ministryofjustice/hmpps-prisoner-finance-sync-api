package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.utils.isMoneyEqual
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
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
      .jsonPath("$.balance").isMoneyEqual(cashBalance)
      .jsonPath("$.code").isEqualTo(cashAccountCode)
      .jsonPath("$.name").isEqualTo("Cash")
      .jsonPath("$.holdBalance").isMoneyEqual(BigDecimal.ZERO)

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, spendsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isMoneyEqual(spendsBalance)
      .jsonPath("$.code").isEqualTo(spendsAccountCode)
      .jsonPath("$.name").isEqualTo("Spends")
      .jsonPath("$.holdBalance").isMoneyEqual(BigDecimal.ZERO)

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts/{accountCode}", prisonNumber, savingsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isMoneyEqual(savingsBalance)
      .jsonPath("$.code").isEqualTo(savingsAccountCode)
      .jsonPath("$.name").isEqualTo("Savings")
      .jsonPath("$.holdBalance").isMoneyEqual(BigDecimal.ZERO)

    webTestClient
      .get()
      .uri("/reconcile/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items.length()").isEqualTo(3)
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $cashAccountCode)].totalBalance").isMoneyEqual(cashBalance)
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $cashAccountCode)].holdBalance").isMoneyEqual(BigDecimal.ZERO)
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $spendsAccountCode)].totalBalance").isMoneyEqual(spendsBalance)
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $spendsAccountCode)].holdBalance").isMoneyEqual(BigDecimal.ZERO)
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $savingsAccountCode)].totalBalance").isMoneyEqual(savingsBalance)
      .jsonPath("$.items[?(@.prisonId == '$prisonId' && @.accountCode == $savingsAccountCode)].holdBalance").isMoneyEqual(BigDecimal.ZERO)

    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}", prisonId, spendsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isMoneyEqual(BigDecimal.ZERO)
      .jsonPath("$.name").isEqualTo("Spends")

    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}", prisonId, savingsAccountCode)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isMoneyEqual(BigDecimal.ZERO)
      .jsonPath("$.name").isEqualTo("Savings")
  }
}
