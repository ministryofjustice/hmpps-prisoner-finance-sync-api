package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.utils.isMoneyEqual
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.MIGRATION_CLEARING_ACCOUNT
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class MigrateGeneralLedgerBalancesTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should throw 400 BAD request when amount has more than 2 decimal places`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val requestBody = GeneralLedgerBalancesSyncRequest(
      accountBalances = listOf(
        GeneralLedgerPointInTimeBalance(accountCode = 2101, balance = BigDecimal("10.001"), asOfTimestamp = LocalDateTime.now()),
      ),
    )

    webTestClient
      .post()
      .uri("/migrate/general-ledger-balances/{prisonId}", prisonId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(requestBody))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `should migrate initial balances for non-prisoner GL accounts correctly`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val accountCode1 = 1501 // Receivable For Earnings (Asset)
    val balance1 = BigDecimal("10000.50")
    val accountCode2 = 2501 // Canteen Payable (Liability)
    val balance2 = BigDecimal("-500.25")

    val localDateTime1 = LocalDateTime.now().minusDays(1)
    val localDateTime2 = LocalDateTime.now()

    val zoneId = ZoneId.of("Europe/London")
    val expectedDate1 = localDateTime1.atZone(zoneId).toInstant()
    val expectedDate2 = localDateTime2.atZone(zoneId).toInstant()

    val requestBody = GeneralLedgerBalancesSyncRequest(
      accountBalances = listOf(
        GeneralLedgerPointInTimeBalance(accountCode = accountCode1, balance = balance1, asOfTimestamp = localDateTime1),
        GeneralLedgerPointInTimeBalance(accountCode = accountCode2, balance = balance2, asOfTimestamp = localDateTime2),
      ),
    )

    webTestClient
      .post()
      .uri("/migrate/general-ledger-balances/{prisonId}", prisonId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(requestBody))
      .exchange()
      .expectStatus().isOk

    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}", prisonId, accountCode1)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isMoneyEqual(balance1)
      .jsonPath("$.code").isEqualTo(accountCode1)
      .jsonPath("$.name").isEqualTo("Receivable For Earnings")

    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}", prisonId, accountCode2)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.balance").isMoneyEqual(balance2)
      .jsonPath("$.code").isEqualTo(accountCode2)
      .jsonPath("$.name").isEqualTo("Canteen Payable")

    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}/transactions", prisonId, accountCode1)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items.length()").isEqualTo(1)
      .jsonPath("$.items[0].date").value<String> { actualDateString ->
        assertThat(Instant.parse(actualDateString))
          .isCloseTo(expectedDate1, within(500, ChronoUnit.MILLIS))
      }

    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts/{accountCode}/transactions", prisonId, accountCode2)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items.length()").isEqualTo(1)
      .jsonPath("$.items[0].date").value<String> { actualDateString ->
        assertThat(Instant.parse(actualDateString))
          .isCloseTo(expectedDate2, within(500, ChronoUnit.MILLIS))
      }
  }

  @Test
  fun `should retrieve gl balances after migration via reconcile endpoint`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()

    val accountBalances = listOf(
      GeneralLedgerPointInTimeBalance(accountCode = 1000, balance = BigDecimal("0.00"), asOfTimestamp = LocalDateTime.parse("2025-03-01T02:31:37.877")),
      GeneralLedgerPointInTimeBalance(accountCode = 1505, balance = BigDecimal("10587.23"), asOfTimestamp = LocalDateTime.parse("2025-03-01T02:31:37.937")),
      GeneralLedgerPointInTimeBalance(accountCode = 2505, balance = BigDecimal("1554565.40"), asOfTimestamp = LocalDateTime.parse("2025-03-28T02:49:19.852")),
      GeneralLedgerPointInTimeBalance(accountCode = 1103, balance = BigDecimal("-906347.40"), asOfTimestamp = LocalDateTime.parse("2025-03-01T02:31:37.900")),
      GeneralLedgerPointInTimeBalance(accountCode = 2503, balance = BigDecimal("1034.63"), asOfTimestamp = LocalDateTime.parse("2025-03-01T02:31:37.992")),
      GeneralLedgerPointInTimeBalance(accountCode = 2100, balance = BigDecimal("0.00"), asOfTimestamp = LocalDateTime.parse("2025-03-01T02:31:37.955")),
      GeneralLedgerPointInTimeBalance(accountCode = 1500, balance = BigDecimal("0.00"), asOfTimestamp = LocalDateTime.parse("2025-03-01T02:31:37.913")),
    )
    val requestBody = GeneralLedgerBalancesSyncRequest(accountBalances = accountBalances)

    webTestClient
      .post()
      .uri("/migrate/general-ledger-balances/{prisonId}", prisonId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(requestBody))
      .exchange()
      .expectStatus().isOk

    webTestClient
      .get()
      .uri("/reconcile/general-ledger-balances/{prisonId}", prisonId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[?(@.accountCode == ${MIGRATION_CLEARING_ACCOUNT})]").isEmpty
      .jsonPath("$.items[?(@.accountCode == 1505)].balance").isMoneyEqual(BigDecimal("10587.23"))
      .jsonPath("$.items[?(@.accountCode == 2505)].balance").isMoneyEqual(BigDecimal("1554565.40"))
      .jsonPath("$.items[?(@.accountCode == 1103)].balance").isMoneyEqual(BigDecimal("-906347.40"))
      .jsonPath("$.items[?(@.accountCode == 2100)].balance").isMoneyEqual(BigDecimal("0.00"))
  }
}
