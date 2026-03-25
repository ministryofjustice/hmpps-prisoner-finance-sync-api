package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniquePrisonNumber
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.utils.isSumMoneyEqual
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class MigrateHoldGLBalanceAndPrisonerWithHoldTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should migrate GL hold balance and verify balance is unchanged after prisoner hold migration`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val prisonNumber = uniquePrisonNumber()

    val privateCashAccountCode = 2101
    val holdsGLAccountCode = 2199
    val generalLedgerHoldBalance = BigDecimal("110.00")

    val migrateGLTimestamp = LocalDateTime.of(2025, 9, 18, 16, 0, 0)
    val migratePrisonerTimestamp = LocalDateTime.of(2025, 9, 18, 17, 0, 0)

    val glMigrationRequestBody = GeneralLedgerBalancesSyncRequest(
      accountBalances = listOf(
        GeneralLedgerPointInTimeBalance(accountCode = holdsGLAccountCode, balance = generalLedgerHoldBalance, asOfTimestamp = migrateGLTimestamp),
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

    webTestClient
      .get()
      .uri("/reconcile/general-ledger-balances/{prisonId}", prisonId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[?(@.accountCode == $holdsGLAccountCode)].balance").isSumMoneyEqual(generalLedgerHoldBalance)

    val prisonerAvailableBalance = BigDecimal("10.00")
    val prisonerHoldBalance = BigDecimal("10.00")

    val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = prisonId,
          accountCode = privateCashAccountCode,
          balance = prisonerAvailableBalance,
          holdBalance = prisonerHoldBalance,
          asOfTimestamp = migratePrisonerTimestamp,
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
      .uri("/reconcile/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[?(@.accountCode == $privateCashAccountCode)].totalBalance").isSumMoneyEqual(prisonerAvailableBalance)
      .jsonPath("$.items[?(@.accountCode == $privateCashAccountCode)].holdBalance").isSumMoneyEqual(prisonerHoldBalance)

    webTestClient
      .get()
      .uri("/reconcile/general-ledger-balances/{prisonId}", prisonId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[?(@.accountCode == $holdsGLAccountCode)].balance").isSumMoneyEqual(generalLedgerHoldBalance)
  }
}
