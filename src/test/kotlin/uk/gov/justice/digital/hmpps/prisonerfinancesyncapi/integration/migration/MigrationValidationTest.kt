package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniquePrisonNumber
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GeneralLedgerDiscrepancyDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.MigrationValidationResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetails
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.abs

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

  @Test
  fun `should return 200 with validated == true and an empty list of discrepancies when balances are able to reconcile`() {
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

    stubForGetGLSubAccountBalances(subAccounts = subAccounts, cashBalance = 200, spendsBalance = 200, savingsBalance = 200)

    val response = webTestClient.post()
      .uri("/validate/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(prisonerBalancesSyncRequest))
      .exchange()
      .expectStatus().isOk
      .expectBody<MigrationValidationResponse>()
      .returnResult()!!

    assert(response.responseBody?.validated == true)
    assertThat(response.responseBody?.discrepancyDetails?.isEmpty())
  }

  @Test
  fun `should return 200 with validated == false and a list of discrepancies of balances that are not able to reconcile`() {
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

    // Incorrect for 2 sub accounts - cash and spends
    stubForGetGLSubAccountBalances(subAccounts = subAccounts, cashBalance = 999, spendsBalance = 999, savingsBalance = 200)

    val response = webTestClient.post()
      .uri("/validate/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(prisonerBalancesSyncRequest))
      .exchange()
      .expectStatus().isOk
      .expectBody<MigrationValidationResponse>()
      .returnResult()!!

    assertThat(response.responseBody?.validated).isFalse()

    val expectedCashDiscrepancy = GeneralLedgerDiscrepancyDetails(
      message = "NOMIS balances do not match with general ledger balances",
      prisonerId = prisonNumber,
      accountType = "CASH",
      legacyAggregatedBalance = 200,
      generalLedgerBalance = 999,
      discrepancy = abs(200 - 999).toLong(),
      glBreakdown = listOf(SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 999)),
      legacyBreakdown = listOf(
        PrisonerEstablishmentBalanceDetails("LEI", 2101, BigDecimal.valueOf(1), BigDecimal.ZERO),
        PrisonerEstablishmentBalanceDetails("MDI", 2101, BigDecimal.valueOf(1), BigDecimal.ZERO),
      ),
    )

    val expectedSpendsDiscrepancy = GeneralLedgerDiscrepancyDetails(
      message = "NOMIS balances do not match with general ledger balances",
      prisonerId = prisonNumber,
      accountType = "SPENDS",
      legacyAggregatedBalance = 200,
      generalLedgerBalance = 999,
      discrepancy = abs(200 - 999).toLong(),
      glBreakdown = listOf(SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 999)),
      legacyBreakdown = listOf(
        PrisonerEstablishmentBalanceDetails("LEI", 2102, BigDecimal.valueOf(1), BigDecimal.ZERO),
        PrisonerEstablishmentBalanceDetails("MDI", 2102, BigDecimal.valueOf(1), BigDecimal.ZERO),
      ),
    )

    assertThat(response.responseBody?.discrepancyDetails)
      .isNotNull
      .hasSize(2)
      .usingRecursiveComparison()
      .ignoringFields("glBreakdown.subAccountId", "glBreakdown.balanceDateTime") // Generated randomly in our stubs
      .ignoringCollectionOrder()
      .isEqualTo(listOf(expectedCashDiscrepancy, expectedSpendsDiscrepancy))
  }

  @Test
  fun `should throw 400 Bad request when amount has more than 2 decimal places`() {
    val prisonNumber = uniquePrisonNumber()
    val prisonerBalancesSyncRequest = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(prisonId = "TEST", accountCode = 2101, balance = BigDecimal("10.001"), holdBalance = BigDecimal.ZERO, asOfTimestamp = LocalDateTime.now(), transactionId = 1234L),
      ),
    )

    webTestClient
      .post()
      .uri("/validate/prisoner-balances/{prisonNumber}", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(prisonerBalancesSyncRequest))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `should throw 404 Not Found when prisoner not found in General Ledger`() {
    val prisonNumber = uniquePrisonNumber()
    val prisonerBalancesSyncRequest = PrisonerBalancesSyncRequest(
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
      .bodyValue(objectMapper.writeValueAsString(prisonerBalancesSyncRequest))
      .exchange()
      .expectStatus().isNotFound
  }
}
