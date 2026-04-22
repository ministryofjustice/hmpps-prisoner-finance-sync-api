package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniquePrisonNumber
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
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

  @BeforeEach
  fun setup() {
    generalLedgerApi.resetAll()
    hmppsAuth.stubGrantToken()
  }

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

  @ParameterizedTest
  @CsvSource("200, 200, 200", "1, 20, 6") // first test will validate, second test will not
  fun `should return 200 when the payload is valid and prisoner exists regardless of whether the balance is validated`(cashBalance: Long, spendsBalance: Long, savingsBalance: Long) {
    val prisonNumber = uniquePrisonNumber()

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

    stubForGetGLSubAccountBalances(subAccounts = subAccounts, cashBalance = cashBalance, spendsBalance = spendsBalance, savingsBalance = savingsBalance)

    webTestClient.post()
      .uri("/validate/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(prisonerBalancesSyncRequest))
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `should throw 400 Bad request when amount has more than 2 decimal places`() {
    val prisonNumber = uniquePrisonNumber()
    val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(prisonId = "TEST", accountCode = 2101, balance = BigDecimal("10.001"), holdBalance = BigDecimal.ZERO, asOfTimestamp = LocalDateTime.now(), transactionId = 1234L),
      ),
    )

    webTestClient
      .post()
      .uri("/validate/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(prisonerMigrationRequestBody))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `should throw 404 Not Found when prisoner not found in General Ledger`() {
    val prisonNumber = uniquePrisonNumber()
    val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = "TEST",
          accountCode = 2101,
          balance = BigDecimal("10.00"),
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = LocalDateTime.now(),
          transactionId =
          1234L,
        ),
      ),
    )

    generalLedgerApi.stubGetAccountNotFound(prisonNumber)

    webTestClient
      .post()
      .uri("/validate/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(prisonerMigrationRequestBody))
      .exchange()
      .expectStatus().isNotFound
  }
}
