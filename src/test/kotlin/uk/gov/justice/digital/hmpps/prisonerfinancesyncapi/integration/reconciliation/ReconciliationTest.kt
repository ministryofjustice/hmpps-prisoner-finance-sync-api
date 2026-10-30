package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.reconciliation

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.AdvancesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.AdvancesApiExtension.Companion.advancesApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HoldsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HoldsApiExtension.Companion.holdsApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AccountAdvanceBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AdvancesAccountReconciliation
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubscriptionsAccountReconciliation
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.ErrorResponse
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class, HoldsApiExtension::class, AdvancesApiExtension::class)
class ReconciliationTest : IntegrationTestBase() {

  @Transactional
  @BeforeEach
  fun setup() {
    integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
  }

  private val testPrisonNumber = "A1234AA"
  private val testPrisonerAccountUUID = UUID.randomUUID()

  @Nested
  @DisplayName("account balance reconciliation")
  inner class AccountBalanceReconciliation {

    @BeforeEach
    fun setup() {
      generalLedgerApi.resetAll()
      holdsApi.resetAll()
      advancesApi.resetAll()
    }

    fun createSubAccountResponse(parentAccountId: UUID, reference: String) = SubAccountResponse(
      id = UUID.randomUUID(),
      parentAccountId = parentAccountId,
      reference = reference,
      createdAt = Instant.now(),
      createdBy = "OMS_OWNER",
    )

    @Test
    fun `should return subscription and advances balances when there are no subaccounts`() {
      generalLedgerApi.stubGetAccount(testPrisonNumber, UUID.randomUUID(), emptyList())
      advancesApi.stubGetAdvanceBalanceForAccount(testPrisonNumber)
      val body = webTestClient
        .get()
        .uri("/reconcile/prisoner-balances/$testPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<AccountReconciliationResponse>().returnResult().responseBody!!

      assertThat(body).isEqualTo(
        AccountReconciliationResponse(
          advances = AdvancesAccountReconciliation(
            advanceCount = 0,
            advanceBalance = BigDecimal("0.00"),
          ),
          subscriptions = SubscriptionsAccountReconciliation(
            subscriptionCount = 0,
            weeklyPaymentAmount = BigDecimal("0"),
          ),
        ),
      ).usingRecursiveComparison()
    }

    @Test
    fun `should return AdvancesAccountReconciliation built from the advances account balance response`() {
      generalLedgerApi.stubGetAccount(testPrisonNumber, UUID.randomUUID(), emptyList())
      val advanceAccountBalanceResponse = AccountAdvanceBalanceResponse(
        advancesAmount = 1000,
        balancesOutstanding = 800,
        weeklyPaymentAmount = 10,
        advanceCount = 2,
        balanceDateTime = Instant.now(),
      )
      advancesApi.stubGetAdvanceBalanceForAccount(testPrisonNumber, advanceAccountBalanceResponse)
      val body = webTestClient
        .get()
        .uri("/reconcile/prisoner-balances/$testPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<AccountReconciliationResponse>().returnResult().responseBody!!

      assertThat(body).isEqualTo(
        AccountReconciliationResponse(
          advances = AdvancesAccountReconciliation(
            advanceCount = 2,
            advanceBalance = BigDecimal("8.00"),
          ),
          subscriptions = SubscriptionsAccountReconciliation(
            subscriptionCount = 0,
            weeklyPaymentAmount = BigDecimal("0"),
          ),
        ),
      ).usingRecursiveComparison()
    }

    @Test
    fun `should include a list of subaccounts if the parent account exists with subaccounts`() {
      val subaccounts = listOf(
        createSubAccountResponse(parentAccountId = testPrisonerAccountUUID, reference = "CASH"),
        createSubAccountResponse(parentAccountId = testPrisonerAccountUUID, reference = "SPENDS"),
        createSubAccountResponse(parentAccountId = testPrisonerAccountUUID, reference = "SAVINGS"),
      )
      advancesApi.stubGetAdvanceBalanceForAccount(testPrisonNumber)
      generalLedgerApi.stubGetAccount(testPrisonNumber, UUID.randomUUID(), subaccounts)
      generalLedgerApi.stubGetSubAccountBalance(subaccounts[0].id, 1000)
      generalLedgerApi.stubGetSubAccountBalance(subaccounts[1].id, 2000)
      generalLedgerApi.stubGetSubAccountBalance(subaccounts[2].id, 0)
      holdsApi.stubGetHoldBalanceForSubAccount(
        testPrisonNumber,
        "CASH",
        1000,
      )
      holdsApi.stubGetHoldBalanceForSubAccount(
        testPrisonNumber,
        "SPENDS",
        0,
      )
      holdsApi.stubGetHoldBalanceForSubAccount(
        testPrisonNumber,
        "SAVINGS",
        2050,
      )

      val body = webTestClient
        .get()
        .uri("/reconcile/prisoner-balances/$testPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        // 2101 is cash - in json this sits at top level
        .jsonPath("$['2101'].totalBalance").isEqualTo(10.00)
        .jsonPath("$['2101'].holdBalance").isEqualTo(10.00)
        // 2102 is spends - in json this sits at top level
        .jsonPath("$['2102'].totalBalance").isEqualTo(20.00)
        .jsonPath("$['2102'].holdBalance").isEqualTo(0.00)
        // 2103 is savings - in json this sits at top level
        .jsonPath("$['2103'].totalBalance").isEqualTo(0.00)
        .jsonPath("$['2103'].holdBalance").isEqualTo(20.50)
        // Advances and subscriptions sit at top level
        .jsonPath("$.advances").exists()
        .jsonPath("$.subscriptions").exists()
    }

    @Test
    fun `should return 404 when a parent account does not exist in general ledger`() {
      generalLedgerApi.stubGetAccountNotFound(testPrisonNumber)
      advancesApi.stubGetAdvanceBalanceForAccount(testPrisonNumber)
      webTestClient
        .get()
        .uri("/reconcile/prisoner-balances/$testPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return a 502 if the advances service returns an error response`() {
      generalLedgerApi.stubGetAccount(testPrisonNumber, UUID.randomUUID(), emptyList())
      advancesApi.stubGetAdvanceBalanceForAccountReturnsError(testPrisonNumber, 500)
      webTestClient
        .get()
        .uri("/reconcile/prisoner-balances/$testPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isEqualTo(502)
    }

    @Test
    fun `should return 502 if general ledger service returns an error response`() {
      val subaccounts = listOf(
        createSubAccountResponse(parentAccountId = testPrisonerAccountUUID, reference = "CASH"),
      )
      generalLedgerApi.stubGetAccount(testPrisonNumber, UUID.randomUUID(), subaccounts)
      generalLedgerApi.stubGetSubAccountBalanceThrowsError(subaccounts[0].id, 500)
      holdsApi.stubGetHoldBalanceForSubAccount(
        testPrisonNumber,
        "CASH",
        1000,
      )
      advancesApi.stubGetAdvanceBalanceForAccount(testPrisonNumber)

      webTestClient
        .get()
        .uri("/reconcile/prisoner-balances/$testPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isEqualTo(502)
        .expectBody<ErrorResponse>().returnResult().responseBody!!
    }

    @Test
    fun `should return 502 if holds service returns an error response`() {
      val subaccounts = listOf(
        createSubAccountResponse(parentAccountId = testPrisonerAccountUUID, reference = "CASH"),
      )
      generalLedgerApi.stubGetAccount(testPrisonNumber, UUID.randomUUID(), subaccounts)
      generalLedgerApi.stubGetSubAccountBalance(subaccounts[0].id, amount = 1000)
      holdsApi.stubGetHoldBalanceForSubAccountReturnError(testPrisonNumber, "CASH", 500)

      webTestClient
        .get()
        .uri("/reconcile/prisoner-balances/$testPrisonNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isEqualTo(502)
        .expectBody<ErrorResponse>().returnResult().responseBody!!
    }
  }
}
