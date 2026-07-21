package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.LedgerAccountMappingService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

const val PRISONER_DISPLAY_ID = "A1234AA"

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

  private lateinit var listAppender: ListAppender<ILoggingEvent>
  private val rootLogger = LoggerFactory.getLogger("uk.gov.justice.digital.hmpps.prisonerfinancesyncapi") as Logger

  @BeforeEach
  fun setup() {
    generalLedgerApi.resetAll()
    hmppsAuth.stubGrantToken()
    listAppender = ListAppender<ILoggingEvent>().apply { start() }
    rootLogger.addAppender(listAppender)
  }

  @AfterEach
  fun tearDown() {
    rootLogger.detachAppender(listAppender)
    nomisSyncPayloadRepository.deleteAll()
    transactionEntryRepository.deleteAll()
    transactionRepository.deleteAll()
    accountRepository.deleteAll()
  }

  fun createSubAccountResponse(subAccountRef: String, parentUUID: UUID) = SubAccountResponse(
    UUID.randomUUID(),
    subAccountRef,
    parentUUID,
    "Test",
    Instant.now(),
  )

  @Nested
  inner class PrisonerBalances {
    @Test
    fun `Should create prisoner Account and Sub Accounts and migrate balances when the accounts are not found`() {
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
    fun `Should aggregate multiple balances of different account codes from different establishments when called`() {
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

      val logs = listAppender.list.map {
        it.formattedMessage + (it.throwableProxy?.let { proxy -> " " + proxy.message } ?: "")
      }

      val matchingLogs = logs.count { it.contains("Successfully migrated balance") }
      assertThat(matchingLogs).isEqualTo(2)

      generalLedgerApi.verify(2, postRequestedFor(urlPathMatching("/sub-accounts.*/balance")))
    }

    @Test
    fun `Should propagate general ledger errors when migrating a balance and return 502`() {
      val accountCode = 2101
      val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
        accountBalances = listOf(
          PrisonerAccountPointInTimeBalance(prisonId = "TEST", accountCode = 2101, balance = BigDecimal("10.01"), holdBalance = BigDecimal.ZERO, asOfTimestamp = LocalDateTime.now(), transactionId = 1234L),
        ),
      )

      val parentAccountId = UUID.randomUUID()

      val subAccount = SubAccountResponse(
        id = UUID.randomUUID(),
        reference = "CASH",
        parentAccountId = parentAccountId,
        createdBy = "TEST",
        createdAt = Instant.now(),
      )

      createSubAccountResponse(
        accountMapping.mapPrisonerSubAccount(accountCode),
        parentAccountId,
      )

      generalLedgerApi.stubGetAccount(PRISONER_DISPLAY_ID, parentAccountId, listOf(subAccount))

      generalLedgerApi.stubPostSubAccountBalanceReturnsError500(subAccount.id)

      webTestClient
        .post()
        .uri("/migrate/prisoner-balances/{prisonNumber}", "A1234AA")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(prisonerMigrationRequestBody))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
    }

    @Test
    fun `Should throw 400 Bad request when amount has more than 2 decimal places`() {
      val prisonerMigrationRequestBody = PrisonerBalancesSyncRequest(
        accountBalances = listOf(
          PrisonerAccountPointInTimeBalance(prisonId = "TEST", accountCode = 2101, balance = BigDecimal("10.001"), holdBalance = BigDecimal.ZERO, asOfTimestamp = LocalDateTime.now(), transactionId = 1234L),
        ),
      )

      webTestClient
        .post()
        .uri("/migrate/prisoner-balances/{prisonNumber}", "A1234AA")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(prisonerMigrationRequestBody))
        .exchange()
        .expectStatus().isBadRequest
    }
  }

  @Nested
  inner class GeneralLedgerBalances {
    @Test
    fun `Should throw 400 BAD request when amount has more than 2 decimal places`() {
      val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
      val requestBody = GeneralLedgerBalancesSyncRequest(
        accountBalances = listOf(
          GeneralLedgerPointInTimeBalance(
            accountCode = 2101,
            balance = BigDecimal("10.001"),
            asOfTimestamp = LocalDateTime.now(),
          ),
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
    fun `Should return 501 not implemented when migrating prison balances`() {
      val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
      val accountCode1 = 1501 // Receivable For Earnings (Asset)
      val balance1 = BigDecimal("10000.50")
      val accountCode2 = 2501 // Canteen Payable (Liability)
      val balance2 = BigDecimal("-500.25")

      val localDateTime1 = LocalDateTime.now().minusDays(1)
      val localDateTime2 = LocalDateTime.now()

      val requestBody = GeneralLedgerBalancesSyncRequest(
        accountBalances = listOf(
          GeneralLedgerPointInTimeBalance(
            accountCode = accountCode1,
            balance = balance1,
            asOfTimestamp = localDateTime1,
          ),
          GeneralLedgerPointInTimeBalance(
            accountCode = accountCode2,
            balance = balance2,
            asOfTimestamp = localDateTime2,
          ),
        ),
      )

      webTestClient
        .post()
        .uri("/migrate/general-ledger-balances/{prisonId}", prisonId)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(requestBody))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }
  }
}
