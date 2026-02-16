package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.apache.commons.lang3.StringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionEntryRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.LedgerAccountMappingService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

const val PRISONER_DISPLAY_ID = "A1234AA"

@TestPropertySource(
  properties = [
    "feature.general-ledger-api.enabled=true",
    "feature.general-ledger-api.test-prisoner-id=$PRISONER_DISPLAY_ID",
  ],
)
@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class MigrateGeneralLedgerPrisonerBalances : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Autowired
  private lateinit var accountMapping: LedgerAccountMappingService

  @Autowired lateinit var nomisSyncPayloadRepository: NomisSyncPayloadRepository

  @Autowired lateinit var transactionRepository: TransactionRepository

  @Autowired lateinit var transactionEntryRepository: TransactionEntryRepository

  @Autowired lateinit var accountRepository: AccountRepository

  @Autowired lateinit var timeConversionService: TimeConversionService

  @BeforeEach
  fun setup() {
    generalLedgerApi.resetAll()
    hmppsAuth.stubGrantToken()
  }

  @AfterEach
  fun tearDown() {
    nomisSyncPayloadRepository.deleteAll()
    transactionEntryRepository.deleteAll()
    transactionRepository.deleteAll()
    accountRepository.deleteAll()
  }

  private fun captureOutputStream(): ByteArrayOutputStream {
    val outputStream = ByteArrayOutputStream()
    val printStream = PrintStream(outputStream)
    System.setOut(printStream)
    return outputStream
  }

  fun createSubAccountResponse(subAccountRef: String, parentUUID: UUID) = SubAccountResponse(
    UUID.randomUUID(),
    subAccountRef,
    parentUUID,
    "Test",
    Instant.now(),
  )

  @Test
  fun `should create prisoner Account and Sub Accounts and migrate balances when the accounts are not found`() {
    val req = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = "TEST",
          accountCode = 2101,
          balance = BigDecimal("100.00"),
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = LocalDateTime.now(),
          transactionId = 9999L,
        ),
        PrisonerAccountPointInTimeBalance(
          prisonId = "LEI",
          accountCode = 2101,
          balance = BigDecimal("120.00"),
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = LocalDateTime.now() - Duration.ofDays(1),
          transactionId = 5555L,
        ),
        PrisonerAccountPointInTimeBalance(
          prisonId = "LEI",
          accountCode = 2102,
          balance = BigDecimal("10.00"),
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = LocalDateTime.now() - Duration.ofDays(2),
          transactionId = 3333L,
        ),
      ),
    )

    val parentAccountId = UUID.randomUUID()
    val subAccounts = mutableMapOf<Int, SubAccountResponse>()

    for (balance in req.accountBalances) {
      subAccounts[balance.accountCode] = createSubAccountResponse(
        accountMapping.mapPrisonerSubAccount(balance.accountCode),
        parentAccountId,
      )
    }

    generalLedgerApi.stubGetAccountNotFound(PRISONER_DISPLAY_ID)
    generalLedgerApi.stubCreateAccount(PRISONER_DISPLAY_ID, parentAccountId)

    for (subAccount in subAccounts.values) {
      generalLedgerApi.stubCreateSubAccount(parentAccountId, subAccount.reference, subAccount.id.toString())

      generalLedgerApi.stubPostSubAccountBalance(
        subAccount.id,
        req.accountBalances
          .filter { accountMapping.mapPrisonerSubAccount(it.accountCode) == subAccount.reference }
          .sumOf { it.balance }.toPence(),
        req.accountBalances
          .filter { accountMapping.mapPrisonerSubAccount(it.accountCode) == subAccount.reference }
          .maxOf { timeConversionService.toUtcInstant(it.asOfTimestamp) },
      )
    }

    webTestClient
      .post()
      .uri("/migrate/prisoner-balances/{prisonNumber}", PRISONER_DISPLAY_ID)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(req))
      .exchange()
      .expectStatus().isOk

    generalLedgerApi.verify(
      1,
      getRequestedFor(urlPathMatching("/accounts"))
        .withQueryParam("reference", equalTo(PRISONER_DISPLAY_ID)),
    )

    generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts")))
    generalLedgerApi.verify(2, postRequestedFor(urlPathMatching("/sub-accounts.*")))

    generalLedgerApi.verify(2, postRequestedFor(urlPathMatching("/sub-accounts.*/balance")))
  }

  @Test
  fun `should aggregate multiple balances of different account codes from different establishments when called`() {
    val req = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = "TEST",
          accountCode = 2101,
          balance = BigDecimal("100.00"),
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = LocalDateTime.now(),
          transactionId = 1234L,
        ),
        PrisonerAccountPointInTimeBalance(
          prisonId = "LEI",
          accountCode = 2101,
          balance = BigDecimal("120.00"),
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = LocalDateTime.now() - Duration.ofDays(1),
          transactionId = 1234L,
        ),
        PrisonerAccountPointInTimeBalance(
          prisonId = "LEI",
          accountCode = 2102,
          balance = BigDecimal("10.00"),
          holdBalance = BigDecimal.ZERO,
          asOfTimestamp = LocalDateTime.now() - Duration.ofDays(2),
          transactionId = 1234L,
        ),
      ),
    )

    val parentAccountId = UUID.randomUUID()
    val subAccounts = mutableMapOf<Int, SubAccountResponse>()

    for (balance in req.accountBalances) {
      subAccounts[balance.accountCode] = createSubAccountResponse(
        accountMapping.mapPrisonerSubAccount(balance.accountCode),
        parentAccountId,
      )
    }

    val outputStream = captureOutputStream()

    generalLedgerApi.stubGetAccount(PRISONER_DISPLAY_ID, parentAccountId, subAccounts.values.toList())

    for (subAccount in subAccounts.values) {
      generalLedgerApi.stubPostSubAccountBalance(
        subAccount.id,
        req.accountBalances
          .filter { accountMapping.mapPrisonerSubAccount(it.accountCode) == subAccount.reference }
          .sumOf { it.balance }.toPence(),
        req.accountBalances
          .filter { accountMapping.mapPrisonerSubAccount(it.accountCode) == subAccount.reference }
          .maxOf { timeConversionService.toUtcInstant(it.asOfTimestamp) },
      )
    }

    webTestClient
      .post()
      .uri("/migrate/prisoner-balances/{prisonNumber}", PRISONER_DISPLAY_ID)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(req))
      .exchange()
      .expectStatus().isOk

    generalLedgerApi.verify(
      1,
      getRequestedFor(urlPathMatching("/accounts"))
        .withQueryParam("reference", equalTo(PRISONER_DISPLAY_ID)),
    )

    assertThat(
      StringUtils.countMatches(
        outputStream.toString(),
        "Successfully migrated balance",
      ),
    )
      .isEqualTo(2)

    generalLedgerApi.verify(2, postRequestedFor(urlPathMatching("/sub-accounts.*/balance")))
  }
}
