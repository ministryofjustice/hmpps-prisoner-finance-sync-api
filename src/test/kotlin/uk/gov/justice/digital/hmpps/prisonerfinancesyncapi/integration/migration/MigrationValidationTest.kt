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

  fun stubForGetAccount(prisonNumber: String): Map<String, Any?> {
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

    return mapOf(
      "reference" to prisonNumber,
      "accountId" to accountId,
      "subAccounts" to subAccounts,
    )
  }

  private fun stubForGetGLSubAccountBalances(subAccounts: List<SubAccountResponse>) {
    generalLedgerApi.stubGetSubAccountBalance(
      accountId = subAccounts[0].id,
      amount = 1L,
    )

    generalLedgerApi.stubGetSubAccountBalance(
      accountId = subAccounts[1].id,
      amount = 1L,
    )

    generalLedgerApi.stubGetSubAccountBalance(
      accountId = subAccounts[2].id,
      amount = 1L,
    )
  }

  @Test
  fun `should return 200 when the payload is valid and prisoner exists`() {
    val prisonNumber = uniquePrisonNumber()

    val mockedNomisAccountBalances = listOf(
      createMockedNomisAccountBalances("LEI", 2101, BigDecimal.valueOf(2.50)),
      createMockedNomisAccountBalances("LEI", 2102, BigDecimal.valueOf(5.00)),
      createMockedNomisAccountBalances("LEI", 2103, BigDecimal.valueOf(10.00)),
      createMockedNomisAccountBalances("MDI", 2101, BigDecimal.valueOf(2.50)),
      createMockedNomisAccountBalances("MDI", 2102, BigDecimal.valueOf(5.00)),
      createMockedNomisAccountBalances("MDI", 2103, BigDecimal.valueOf(10.00)),
    )

    val prisonerBalancesSyncRequest = PrisonerBalancesSyncRequest(
      accountBalances = mockedNomisAccountBalances,
    )

    val accountResponse = stubForGetAccount(prisonNumber)

    // stub account balances (from service test)
    val subAccounts = accountResponse["subAccounts"] as List<SubAccountResponse>
    stubForGetGLSubAccountBalances(subAccounts)

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
