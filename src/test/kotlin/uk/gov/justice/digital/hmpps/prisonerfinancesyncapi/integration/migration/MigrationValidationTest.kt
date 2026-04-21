package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniquePrisonNumber
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

class MigrationValidationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  fun createMockedNomisAccountBalances(prisonId: String, accountCode: Int, balance: BigDecimal) = PrisonerAccountPointInTimeBalance(
    prisonId = prisonId,
    accountCode = accountCode,
    balance = balance,
    holdBalance = BigDecimal.valueOf(0),
    transactionId = 3L,
    asOfTimestamp = LocalDateTime.now(),
  )

  fun stubForGetAccount(prisonNumber: String): List<SubAccountResponse> {
    val accountId = UUID.randomUUID()

    val cashSubAccountId = UUID.randomUUID()
    val spendsSubAccountId = UUID.randomUUID()
    val savingsSubAccountId = UUID.randomUUID()

    val subAccounts = listOf(
      SubAccountResponse(
        id = cashSubAccountId,
        reference = "CASH",
        parentAccountId = accountId,
        createdBy = "",
        createdAt = Instant.now(),
      ),
      SubAccountResponse(
        id = spendsSubAccountId,
        reference = "SPENDS",
        parentAccountId = accountId,
        createdBy = "",
        createdAt = Instant.now(),
      ),
      SubAccountResponse(
        id = savingsSubAccountId,
        reference = "SAVINGS",
        parentAccountId = accountId,
        createdBy = "",
        createdAt = Instant.now(),
      ),
    )

    generalLedgerApi.stubGetAccount(
      reference = prisonNumber,
      returnUuid = accountId,
      subAccounts = subAccounts,
    )

    return subAccounts
  }

  private fun stubForGetGLSubAccountBalances(subAccounts: List<SubAccountResponse>, cashBalance: Long, spendsBalance: Long, savingsBalance: Long) {
    generalLedgerApi.stubGetSubAccountBalance(
      accountId = subAccounts[0].id,
      amount = cashBalance,
    )

    generalLedgerApi.stubGetSubAccountBalance(
      accountId = subAccounts[1].id,
      amount = spendsBalance,
    )

    generalLedgerApi.stubGetSubAccountBalance(
      accountId = subAccounts[2].id,
      amount = savingsBalance,
    )
  }

  // @ParameterizedTest
  // @CsvSource("200, 200, 200", "1, 20, 6") // first test will validate, second test will not
  @Test
  fun `should return 200 when the payload is valid and prisoner exists regardless of whether the balance is validated`() {
    val prisonNumber = uniquePrisonNumber()

    val cashBalance = 200L
    val spendsBalance = 200L
    val savingsBalance = 200L

    val mockedNomisAccountBalances = listOf(
      createMockedNomisAccountBalances("LEI", 2101, BigDecimal.valueOf(1)),
      createMockedNomisAccountBalances("LEI", 2102, BigDecimal.valueOf(1)),
      createMockedNomisAccountBalances("LEI", 2103, BigDecimal.valueOf(1)),
      createMockedNomisAccountBalances("MDI", 2101, BigDecimal.valueOf(1)),
      createMockedNomisAccountBalances("MDI", 2102, BigDecimal.valueOf(1)),
      createMockedNomisAccountBalances("MDI", 2103, BigDecimal.valueOf(1)),
    )

    val prisonerBalancesSyncRequest = PrisonerBalancesSyncRequest(
      accountBalances = mockedNomisAccountBalances,
    )

    val subAccounts = stubForGetAccount(prisonNumber)

    // stub account balances (from service test)
    stubForGetGLSubAccountBalances(subAccounts = subAccounts, cashBalance = cashBalance, spendsBalance = spendsBalance, savingsBalance = savingsBalance)

    // call the end point expect 200
    webTestClient.post()
      .uri("/validate/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(prisonerBalancesSyncRequest))
      .exchange()
      .expectStatus().isOk
  }
}
