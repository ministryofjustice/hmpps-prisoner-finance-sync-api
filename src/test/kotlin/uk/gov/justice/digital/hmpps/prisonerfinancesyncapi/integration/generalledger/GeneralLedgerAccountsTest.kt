package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.generalledger

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
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
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GeneralLedgerDiscrepancyDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.LedgerAccountMappingService
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

@TestPropertySource(
  properties = [
    "feature.general-ledger-api.enabled=true",
    "feature.general-ledger-api.test-prisoner-id=A1234AA",
  ],
)
@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class GeneralLedgerAccountsTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Autowired lateinit var nomisSyncPayloadRepository: NomisSyncPayloadRepository

  @Autowired lateinit var transactionRepository: TransactionRepository

  @Autowired lateinit var transactionEntryRepository: TransactionEntryRepository

  @Autowired lateinit var accountRepository: AccountRepository

  private val testPrisonerId = "A1234AA"

  @Autowired
  private lateinit var accountMapping: LedgerAccountMappingService

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
    nomisSyncPayloadRepository.deleteAll()
    transactionEntryRepository.deleteAll()
    transactionRepository.deleteAll()
    accountRepository.deleteAll()
  }

  private fun makeSubAccountResponse(reference: String, parentAccountId: UUID) = SubAccountResponse(
    UUID.randomUUID(),
    reference,
    parentAccountId,
    "TEST",
    Instant.now(),
  )

  @Nested
  @DisplayName("accountTests")
  inner class AccountTests {

    @Test
    fun `should propagate RetryAfterConflictException when accountResolver fails in get sub account`() {
      val caseloadId = "TES"
      val transaction =
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1L,
          offenderDisplayId = testPrisonerId,
          offenderBookingId = 100L,
          subAccountType = "",
          postingType = "DR",
          type = "ATOF",
          description = "Test Transaction",
          amount = BigDecimal("10.00"),
          reference = "REF",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 1501, "DR", BigDecimal("10.00")),
            GeneralLedgerEntry(2, 2102, "CR", BigDecimal("10.00")),
          ),
        )
      val request = createRequest(testPrisonerId, caseloadId, listOf(transaction))

      val prisonerAccId = UUID.randomUUID()
      val prisonAccId = UUID.randomUUID()

      generalLedgerApi.stubGetAccount(testPrisonerId, prisonerAccId)
      generalLedgerApi.stubGetAccount(
        request.caseloadId,
        prisonAccId,
        listOf(
          makeSubAccountResponse(
            accountMapping.mapPrisonSubAccount(transaction.generalLedgerEntries[0].code, transaction.type),
            prisonAccId,
          ),
        ),
      )

      val prisonerRef = accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code)

      generalLedgerApi.stubCreateSubAccountReturnsConflict(prisonerAccId, prisonerRef)

      generalLedgerApi.stubGetSubAccountNotFound(
        testPrisonerId,
        prisonerRef,
      )

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      val logs = listAppender.list.map {
        it.formattedMessage + (it.throwableProxy?.let { proxy -> " " + proxy.message } ?: "")
      }
      assertThat(logs).anyMatch { it.contains("Sub account not found after server responded with 409 for reference: $prisonerRef") }

      generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))
    }

    @Test
    fun `should propagate RetryAfterConflictException when accountResolver fails to get parent account on the second try`() {
      val caseloadId = "TES"
      val request = createRequest(testPrisonerId, caseloadId)

      val scenario = "ConflictScenario"
      val secondState = "SECOND_CALL"
      generalLedgerApi.stubGetAccountNotFound(testPrisonerId, scenario, STARTED, secondState)

      generalLedgerApi.stubCreateAccountReturnsConflict(testPrisonerId)

      generalLedgerApi.stubGetAccountNotFound(testPrisonerId, scenarioName = scenario, scenarioState = secondState)

      webTestClient
        .post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      val logs = listAppender.list.map {
        it.formattedMessage + (it.throwableProxy?.let { proxy -> " " + proxy.message } ?: "")
      }
      assertThat(logs).anyMatch { it.contains("Account not found after server responded with 409 for reference: $testPrisonerId") }

      generalLedgerApi.verify(
        2,
        getRequestedFor(urlPathEqualTo("/accounts"))
          .withQueryParam("reference", equalTo(testPrisonerId)),
      )
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))
    }

    @Test
    fun `should propagate Exception when accountResolver fails in create sub account`() {
      val caseloadId = "TES"
      val transaction =
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1L,
          offenderDisplayId = testPrisonerId,
          offenderBookingId = 100L,
          subAccountType = "",
          postingType = "DR",
          type = "ATOF",
          description = "Test Transaction",
          amount = BigDecimal("10.00"),
          reference = "REF",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 1501, "DR", BigDecimal("10.00")),
            GeneralLedgerEntry(2, 2102, "CR", BigDecimal("10.00")),
          ),
        )
      val request = createRequest(testPrisonerId, caseloadId, listOf(transaction))

      val prisonerAccId = UUID.randomUUID()
      val prisonAccId = UUID.randomUUID()

      generalLedgerApi.stubGetAccount(testPrisonerId, prisonerAccId)
      generalLedgerApi.stubGetAccount(
        request.caseloadId,
        prisonAccId,
        listOf(
          makeSubAccountResponse(
            accountMapping.mapPrisonSubAccount(transaction.generalLedgerEntries[0].code, transaction.type),
            prisonAccId,
          ),
        ),
      )

      val prisonerRef = accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code)

      generalLedgerApi.stubCreateSubAccountReturnsServerError(prisonerAccId, prisonerRef)

      generalLedgerApi.stubGetSubAccount(
        testPrisonerId,
        prisonerRef,
      )

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      val logs = listAppender.list.map {
        it.formattedMessage + (it.throwableProxy?.let { proxy -> " " + proxy.message } ?: "")
      }
      assertThat(logs).anyMatch { it.contains("GL Server Error") }

      generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))
    }

    @Test
    fun `should propagate Exception when accountResolver fails in create parent account`() {
      val caseloadId = "TES"
      val request = createRequest(testPrisonerId, caseloadId)

      val scenario = "ConflictScenario"
      val secondState = "SECOND_CALL"
      generalLedgerApi.stubGetAccountNotFound(testPrisonerId, scenario, STARTED, secondState)

      val parentAccountId = UUID.randomUUID()

      generalLedgerApi.stubCreateAccountReturnsServerError(testPrisonerId)

      generalLedgerApi.stubGetAccount(testPrisonerId, parentAccountId, scenarioName = scenario, scenarioState = secondState)

      webTestClient
        .post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      val logs = listAppender.list.map {
        it.formattedMessage + (it.throwableProxy?.let { proxy -> " " + proxy.message } ?: "")
      }
      assertThat(logs).anyMatch { it.contains("GL Server Error") }

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/accounts"))
          .withQueryParam("reference", equalTo(testPrisonerId)),
      )
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))
    }

    @Test
    fun `should find parent account again when post sub account returns conflict 409`() {
      val caseloadId = "TES"
      val transaction =
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1L,
          offenderDisplayId = testPrisonerId,
          offenderBookingId = 100L,
          subAccountType = "",
          postingType = "DR",
          type = "ATOF",
          description = "Test Transaction",
          amount = BigDecimal("10.00"),
          reference = "REF",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 1501, "DR", BigDecimal("10.00")),
            GeneralLedgerEntry(2, 2102, "CR", BigDecimal("10.00")),
          ),
        )
      val request = createRequest(testPrisonerId, caseloadId, listOf(transaction))

      val prisonerAccId = UUID.randomUUID()
      val prisonAccId = UUID.randomUUID()

      generalLedgerApi.stubGetAccount(testPrisonerId, prisonerAccId)
      generalLedgerApi.stubGetAccount(
        request.caseloadId,
        prisonAccId,
        listOf(
          makeSubAccountResponse(
            accountMapping.mapPrisonSubAccount(transaction.generalLedgerEntries[0].code, transaction.type),
            prisonAccId,
          ),
        ),
      )

      val prisonerRef = accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code)

      generalLedgerApi.stubCreateSubAccountReturnsConflict(prisonerAccId, prisonerRef)

      generalLedgerApi.stubGetSubAccount(
        testPrisonerId,
        prisonerRef,
      )

      generalLedgerApi.stubPostTransaction()

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
    }

    @Test
    fun `should find parent account again when post parent account returns conflict 409`() {
      val caseloadId = "TES"
      val request = createRequest(testPrisonerId, caseloadId)

      val scenario = "ConflictScenario"
      val secondState = "SECOND_CALL"
      generalLedgerApi.stubGetAccountNotFound(testPrisonerId, scenario, STARTED, secondState)

      val prisonerRef1 = accountMapping.mapPrisonerSubAccount(
        request.offenderTransactions[0].generalLedgerEntries[0].code,
      )
      val prisonerRef2 = accountMapping.mapPrisonerSubAccount(
        request.offenderTransactions[0].generalLedgerEntries[1].code,
      )

      val parentAccountId = UUID.randomUUID()

      generalLedgerApi.stubCreateAccountReturnsConflict(testPrisonerId)

      generalLedgerApi.stubGetAccount(testPrisonerId, parentAccountId, scenarioName = scenario, scenarioState = secondState)

      generalLedgerApi.stubCreateSubAccount(parentAccountId, prisonerRef1)
      generalLedgerApi.stubCreateSubAccount(parentAccountId, prisonerRef2)

      generalLedgerApi.stubPostTransaction()

      webTestClient
        .post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verify(
        2,
        getRequestedFor(urlPathEqualTo("/accounts"))
          .withQueryParam("reference", equalTo(testPrisonerId)),
      )

      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts")))
      generalLedgerApi.verify(2, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
    }

    @Test
    fun `should lookup and create prisoner SUB accounts when account doesnt exist`() {
      val transaction =
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1L,
          offenderDisplayId = testPrisonerId,
          offenderBookingId = 100L,
          subAccountType = "",
          postingType = "DR",
          type = "ATOF",
          description = "Test Transaction",
          amount = BigDecimal("10.00"),
          reference = "REF",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 1501, "DR", BigDecimal("10.00")),
            GeneralLedgerEntry(2, 2102, "CR", BigDecimal("10.00")),
          ),
        )
      val request = createRequest(testPrisonerId, "TES", listOf(transaction))

      val prisonerAccId = UUID.randomUUID()
      val prisonAccId = UUID.randomUUID()

      generalLedgerApi.stubGetAccount(testPrisonerId, prisonerAccId)
      generalLedgerApi.stubGetAccount(
        request.caseloadId,
        prisonAccId,
        listOf(
          makeSubAccountResponse(
            accountMapping.mapPrisonSubAccount(transaction.generalLedgerEntries[0].code, transaction.type),
            prisonAccId,
          ),
        ),
      )

      val prisonerRef = accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code)

      generalLedgerApi.stubGetSubAccountNotFound(
        testPrisonerId,
        prisonerRef,
      )

      generalLedgerApi.stubCreateSubAccount(prisonerAccId, prisonerRef)
      generalLedgerApi.stubPostTransaction()

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
    }

    @Test
    fun `should lookup and create prison SUB accounts when account doesnt exist`() {
      val transaction =
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1L,
          offenderDisplayId = testPrisonerId,
          offenderBookingId = 100L,
          subAccountType = "",
          postingType = "DR",
          type = "ATOF",
          description = "Test Transaction",
          amount = BigDecimal("10.00"),
          reference = "REF",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 1501, "DR", BigDecimal("10.00")),
            GeneralLedgerEntry(2, 2103, "CR", BigDecimal("10.00")),
          ),
        )
      val request = createRequest(testPrisonerId, "TES", listOf(transaction))

      val prisonerAccId = UUID.randomUUID()
      val prisonAccId = UUID.randomUUID()

      generalLedgerApi.stubGetAccount(
        testPrisonerId,
        prisonerAccId,
        listOf(
          makeSubAccountResponse(
            accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code),
            prisonerAccId,
          ),
        ),
      )
      generalLedgerApi.stubGetAccount(request.caseloadId, prisonAccId)

      val prisonRef = accountMapping.mapPrisonSubAccount(
        transaction.generalLedgerEntries[0].code,
        request.offenderTransactions[0].type,
      )

      generalLedgerApi.stubCreateSubAccount(prisonAccId, prisonRef)

      generalLedgerApi.stubPostTransaction()

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
    }

    @Test
    fun `should find existing prison SUB account and not create new one when sub accounts are returned by parent`() {
      val transaction =
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1L,
          offenderDisplayId = testPrisonerId,
          offenderBookingId = 100L,
          subAccountType = "",
          postingType = "DR",
          type = "ATOF",
          description = "Test Transaction",
          amount = BigDecimal("10.00"),
          reference = "REF",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 1501, "DR", BigDecimal("10.00")),
            GeneralLedgerEntry(2, 2101, "CR", BigDecimal("10.00")),
          ),
        )
      val request = createRequest(testPrisonerId, "TES", listOf(transaction))

      val prisonerAccId = UUID.randomUUID()
      val prisonAccId = UUID.randomUUID()

      val prisonRef = accountMapping.mapPrisonSubAccount(
        transaction.generalLedgerEntries[0].code,
        request.offenderTransactions[0].type,
      )
      val prisonerRef = accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code)

      generalLedgerApi.stubGetAccount(
        request.caseloadId,
        prisonAccId,
        listOf(
          makeSubAccountResponse(
            prisonRef,
            prisonAccId,
          ),
        ),
      )

      generalLedgerApi.stubGetAccount(
        testPrisonerId,
        prisonerAccId,
        listOf(
          makeSubAccountResponse(
            prisonerRef,
            prisonAccId,
          ),
        ),
      )

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verify(0, getRequestedFor(urlPathMatching("/sub-accounts")))
      generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
    }

    @Test
    fun `should find existing prisoner and prison SUB accounts and not create new ones`() {
      val transaction =
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1L,
          offenderDisplayId = testPrisonerId,
          offenderBookingId = 100L,
          subAccountType = "SPND",
          postingType = "DR",
          type = "CANT",
          description = "Test Transaction",
          amount = BigDecimal("10.00"),
          reference = "REF",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 1501, "DR", BigDecimal("10.00")),
            GeneralLedgerEntry(2, 2101, "CR", BigDecimal("10.00")),
          ),
        )
      val request = createRequest(testPrisonerId, "TES", listOf(transaction))

      val prisonerAccId = UUID.randomUUID()
      val prisonAccId = UUID.randomUUID()

      generalLedgerApi.stubGetAccount(
        testPrisonerId,
        prisonerAccId,
        listOf(
          makeSubAccountResponse(accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code), prisonerAccId),
        ),
      )
      generalLedgerApi.stubGetAccount(
        request.caseloadId,
        prisonAccId,
        listOf(
          makeSubAccountResponse(
            accountMapping.mapPrisonSubAccount(transaction.generalLedgerEntries[0].code, transaction.type),
            prisonAccId,
          ),
        ),
      )

      generalLedgerApi.stubPostTransaction()

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verify(0, getRequestedFor(urlPathMatching("/sub-accounts.*")))
      generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
    }

    @Test
    fun `should call general ledger to lookup an account and create it if not exists`() {
      val caseloadId = "TES"
      val request = createRequest(testPrisonerId, caseloadId)

      generalLedgerApi.stubGetAccountNotFound(testPrisonerId)

      val prisonerRef1 = accountMapping.mapPrisonerSubAccount(
        request.offenderTransactions[0].generalLedgerEntries[0].code,
      )
      val prisonerRef2 = accountMapping.mapPrisonerSubAccount(
        request.offenderTransactions[0].generalLedgerEntries[1].code,
      )

      val parentAccountId = UUID.randomUUID()

      generalLedgerApi.stubCreateAccount(
        testPrisonerId,
        parentAccountId,
      )

      generalLedgerApi.stubCreateSubAccount(parentAccountId, prisonerRef1)
      generalLedgerApi.stubCreateSubAccount(parentAccountId, prisonerRef2)

      generalLedgerApi.stubPostTransaction()

      webTestClient
        .post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verify(
        getRequestedFor(urlPathEqualTo("/accounts"))
          .withQueryParam("reference", equalTo(testPrisonerId)),
      )

      generalLedgerApi.verify(2, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
    }

    @Test
    fun `should successfully sync to internal ledger when general ledger is down`() {
      generalLedgerApi.stubFor(
        get(urlPathEqualTo("/accounts"))
          .withQueryParam("reference", equalTo(testPrisonerId))
          .willReturn(
            aResponse()
              .withStatus(500)
              .withBody("Internal Server Error"),
          ),
      )

      val request = createRequest(testPrisonerId)

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated
    }

    @Test
    fun `should not call General Ledger for non-test prisoners`() {
      val otherPrisonerId = "Z9999ZZ"
      val request = createRequest(otherPrisonerId)

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verify(0, getRequestedFor(urlPathEqualTo("/accounts")))
    }

    private fun createRequest(
      offenderId: String,
      caseloadId: String = "MDI",
      offenderTransactions: List<OffenderTransaction> = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1L,
          offenderDisplayId = offenderId,
          offenderBookingId = 100L,
          subAccountType = "SPND",
          postingType = "DR",
          type = "CANT",
          description = "Test Transaction",
          amount = BigDecimal("10.00"),
          reference = "REF",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 2102, "DR", BigDecimal("10.00")),
            GeneralLedgerEntry(2, 2101, "CR", BigDecimal("10.00")),
          ),
        ),
      ),
    ): SyncOffenderTransactionRequest {
      val randomTxId = Random.nextLong(100000, 999999)

      return SyncOffenderTransactionRequest(
        transactionId = randomTxId,
        requestId = UUID.randomUUID(),
        caseloadId = caseloadId,
        transactionTimestamp = LocalDateTime.now(),
        createdAt = LocalDateTime.now(),
        createdBy = "TEST",
        createdByDisplayName = "Test User",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = offenderTransactions,
      )
    }
  }

  @Nested
  @DisplayName("transactionTests")
  inner class TransactionTests {

    @Test
    fun `should record 'Advance' transaction to general ledger`() {
      val prisonId = "LEI"
      val amount = BigDecimal("5.00")

      val prisonerSubRef = "SPENDS"
      val prisonSubRef = "1502:ADV"

      val prisonerParentUuid = UUID.randomUUID()
      val prisonerSubUuid = UUID.randomUUID().toString()
      val prisonParentUuid = UUID.randomUUID()
      val prisonSubUuid = UUID.randomUUID().toString()

      generalLedgerApi.stubGetAccountNotFound(testPrisonerId)
      generalLedgerApi.stubCreateAccount(testPrisonerId, prisonerParentUuid)
      generalLedgerApi.stubGetSubAccountNotFound(testPrisonerId, prisonerSubRef)
      generalLedgerApi.stubCreateSubAccount(prisonerParentUuid, prisonerSubRef, prisonerSubUuid)

      generalLedgerApi.stubGetAccountNotFound(prisonId)
      generalLedgerApi.stubCreateAccount(prisonId, prisonParentUuid)
      generalLedgerApi.stubGetSubAccountNotFound(prisonId, prisonSubRef)
      generalLedgerApi.stubCreateSubAccount(prisonParentUuid, prisonSubRef, prisonSubUuid)

      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()

      val request = SyncOffenderTransactionRequest(
        transactionId = transactionId,
        caseloadId = prisonId,
        transactionTimestamp = timestamp,
        createdAt = timestamp.plusSeconds(5),
        createdBy = "OMS_OWNER",
        requestId = UUID.randomUUID(),
        createdByDisplayName = "OMS_OWNER",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = listOf(
          OffenderTransaction(
            entrySequence = 1,
            offenderId = 5306470,
            offenderDisplayId = testPrisonerId,
            offenderBookingId = 2970777,
            subAccountType = "SPND",
            postingType = "CR",
            type = "ADV",
            description = "Test Transaction for Balance Check",
            amount = amount,
            reference = "REF-$transactionId",
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, 2102, "CR", amount),
              GeneralLedgerEntry(2, 1502, "DR", amount),
            ),
          ),
        ),
      )

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated
        .expectBody()
        .jsonPath("$.action").isEqualTo("CREATED")

      generalLedgerApi.verifyCreateAccount(testPrisonerId)
      generalLedgerApi.verifyCreateSubAccount(prisonerParentUuid.toString(), prisonerSubRef)

      generalLedgerApi.verifyCreateAccount(prisonId)
      generalLedgerApi.verifyCreateSubAccount(prisonParentUuid.toString(), prisonSubRef)

      generalLedgerApi.verifyTransactionPosted()
    }

    @Disabled("Impossible to trigger as the internal sync service fails first")
    @Test
    fun `should throw exception when there are no transactions`() {
      val prisonId = "LEI"

      generalLedgerApi.stubGetAccount(prisonId)

      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()

      val request = SyncOffenderTransactionRequest(
        transactionId = transactionId,
        caseloadId = prisonId,
        transactionTimestamp = timestamp,
        createdAt = timestamp.plusSeconds(5),
        createdBy = "OMS_OWNER",
        requestId = UUID.randomUUID(),
        createdByDisplayName = "OMS_OWNER",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = emptyList(),
      )

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated
        .expectBody()
        .jsonPath("$.action").isEqualTo("CREATED")

      generalLedgerApi.verifyCreateAccount(prisonId)

      generalLedgerApi.verifyTransactionPosted(times = 0)
    }

    @Test
    fun `should record multi-prisoner 'Canteen' transaction to general ledger`() {
      val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()

      val canteenSubRef = "2501:CANT"
      val spendsSubRef = "SPENDS"

      val prisoner1 = testPrisonerId
      val amount1 = BigDecimal("1.40")

      val prisoner2 = "PRISONER_2"
      val amount2 = BigDecimal("2.20")

      val prisoner1ParentUuid = UUID.randomUUID()
      val prisoner1SubUuid = UUID.randomUUID().toString()
      val prisoner2ParentUuid = UUID.randomUUID()
      val prisoner2SubUuid = UUID.randomUUID().toString()
      val prisonParentUuid = UUID.randomUUID()
      val canteenSubUuid = UUID.randomUUID().toString()

      generalLedgerApi.stubGetAccountNotFound(prisoner1)
      generalLedgerApi.stubCreateAccount(prisoner1, prisoner1ParentUuid)
      generalLedgerApi.stubGetSubAccountNotFound(prisoner1, spendsSubRef)
      generalLedgerApi.stubCreateSubAccount(prisoner1ParentUuid, spendsSubRef, prisoner1SubUuid)

      generalLedgerApi.stubGetAccountNotFound(prisoner2)
      generalLedgerApi.stubCreateAccount(prisoner2, prisoner2ParentUuid)
      generalLedgerApi.stubGetSubAccountNotFound(prisoner2, spendsSubRef)
      generalLedgerApi.stubCreateSubAccount(prisoner2ParentUuid, spendsSubRef, prisoner2SubUuid)

      generalLedgerApi.stubGetAccountNotFound(prisonId)
      generalLedgerApi.stubCreateAccount(prisonId, prisonParentUuid)
      generalLedgerApi.stubGetSubAccountNotFound(prisonId, canteenSubRef)
      generalLedgerApi.stubCreateSubAccount(prisonParentUuid, canteenSubRef, canteenSubUuid)

      generalLedgerApi.stubPostTransaction(debtorSubAccountUuid = prisoner1SubUuid, creditorSubAccountUuid = canteenSubUuid)
      generalLedgerApi.stubPostTransaction(debtorSubAccountUuid = prisoner2SubUuid, creditorSubAccountUuid = canteenSubUuid)

      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()

      val request = SyncOffenderTransactionRequest(
        transactionId = transactionId,
        requestId = UUID.randomUUID(),
        caseloadId = prisonId,
        transactionTimestamp = timestamp,
        createdAt = timestamp.plusSeconds(5),
        createdBy = "OMS_OWNER",
        createdByDisplayName = "Jeffrey",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = listOf(
          OffenderTransaction(
            entrySequence = 1,
            offenderId = 2605754,
            offenderDisplayId = prisoner1,
            offenderBookingId = 1223356,
            subAccountType = "SPND",
            postingType = "DR",
            type = "CANT",
            description = "Canteen Spend",
            amount = amount1,
            reference = null,
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, 2102, "DR", amount1),
              GeneralLedgerEntry(2, 2501, "CR", amount1),
            ),
          ),
          OffenderTransaction(
            entrySequence = 2,
            offenderId = 4305755,
            offenderDisplayId = prisoner2,
            offenderBookingId = 789567,
            subAccountType = "SPND",
            postingType = "DR",
            type = "CANT",
            description = "Canteen Spend",
            amount = amount2,
            reference = null,
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, 2102, "DR", amount2),
              GeneralLedgerEntry(2, 2501, "CR", amount2),
            ),
          ),
        ),
      )

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verifyCreateAccount(prisoner1)

      generalLedgerApi.verifyCreateAccount(prisoner2)

      generalLedgerApi.verifyCreateAccount(prisonId)
      generalLedgerApi.verifyTransactionPosted(times = 2)
    }

    @Test
    fun `should record 'Sub-Account Transfer' transaction to general ledger`() {
      val prisonId = "LEI"
      val amount = BigDecimal("12.00")

      val spendsSubRef = "SPENDS"
      val cashSubRef = "CASH"

      val prisonerParentUuid = UUID.randomUUID()
      val spendsSubUuid = UUID.randomUUID().toString()
      val cashSubUuid = UUID.randomUUID().toString()

      generalLedgerApi.stubGetAccount(prisonId)

      generalLedgerApi.stubGetAccountNotFound(testPrisonerId)
      generalLedgerApi.stubCreateAccount(testPrisonerId, prisonerParentUuid)

      generalLedgerApi.stubGetSubAccountNotFound(testPrisonerId, spendsSubRef)
      generalLedgerApi.stubCreateSubAccount(prisonerParentUuid, spendsSubRef, spendsSubUuid)

      generalLedgerApi.stubGetSubAccountNotFound(testPrisonerId, cashSubRef)
      generalLedgerApi.stubCreateSubAccount(prisonerParentUuid, cashSubRef, cashSubUuid)

      generalLedgerApi.stubPostTransaction(
        debtorSubAccountUuid = spendsSubUuid, // DR Spends
        creditorSubAccountUuid = cashSubUuid, // CR Cash
      )

      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()

      val request = SyncOffenderTransactionRequest(
        transactionId = transactionId,
        requestId = UUID.fromString("82f6a7bf-bae2-44ed-8573-46c84c41dc3e"),
        caseloadId = prisonId,
        transactionTimestamp = timestamp,
        createdAt = timestamp,
        createdBy = "OMS_OWNER",
        createdByDisplayName = "OMS_OWNER",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = listOf(
          OffenderTransaction(
            entrySequence = 1,
            offenderId = 2607103,
            offenderDisplayId = testPrisonerId,
            offenderBookingId = 1227181,
            subAccountType = "SPND",
            postingType = "DR",
            type = "OT",
            description = "Sub-Account Transfer",
            amount = amount,
            reference = null,
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, 2102, "DR", amount),
              GeneralLedgerEntry(2, 2101, "CR", amount),
            ),
          ),
        ),
      )

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verifyCreateAccount(testPrisonerId)
      generalLedgerApi.verifyCreateSubAccount(prisonerParentUuid.toString(), spendsSubRef)
      generalLedgerApi.verifyCreateSubAccount(prisonerParentUuid.toString(), cashSubRef)

      generalLedgerApi.verifyTransactionPosted()
    }
  }

  @Nested
  @DisplayName("reconciliationTests")
  inner class ReconciliationTests {
    fun createSubAccountResponse(parentAccountId: UUID, reference: String) = SubAccountResponse(
      id = UUID.randomUUID(),
      parentAccountId = parentAccountId,
      reference = reference,
      createdAt = Instant.now(),
      createdBy = "OMS_OWNER",
    )

    @Test
    fun `should show balance discrepancy for a prisoner when general ledger and legacy GL amounts are different`() {
      // mock Internal Ledger
      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()

      val request = SyncOffenderTransactionRequest(
        transactionId = transactionId,
        requestId = UUID.randomUUID(),
        caseloadId = "TES",
        transactionTimestamp = timestamp,
        createdAt = timestamp,
        createdBy = "OMS_OWNER",
        createdByDisplayName = "OMS_OWNER",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = listOf(
          OffenderTransaction(
            entrySequence = 1,
            offenderId = 2607103,
            offenderDisplayId = testPrisonerId,
            offenderBookingId = 1227181,
            subAccountType = "SPND",
            postingType = "DR",
            type = "OT",
            description = "Sub-Account Transfer",
            amount = BigDecimal("30.0"),
            reference = null,
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, 1501, "DR", BigDecimal("30.0")),
              GeneralLedgerEntry(2, 2101, "CR", BigDecimal("10.0")),
              GeneralLedgerEntry(3, 2102, "CR", BigDecimal("10.0")),
              GeneralLedgerEntry(4, 2103, "CR", BigDecimal("10.0")),
            ),
          ),
        ),
      )

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      // mock GL
      val prisonerAccId = UUID.randomUUID()

      val subAccountResponses = accountMapping.prisonerSubAccounts.map { kv ->
        createSubAccountResponse(prisonerAccId, kv.key)
      }.toList()

      generalLedgerApi.stubGetAccount(testPrisonerId, prisonerAccId, subAccountResponses)

      val subAccountReturnedResponses = mutableListOf<SubAccountBalanceResponse>()

      subAccountResponses.forEach { subAccount ->
        subAccountReturnedResponses.add(generalLedgerApi.stubGetSubAccountBalance(subAccount.id, 100))
      }

      webTestClient
        .get()
        .uri("/reconcile/prisoner-balances/$testPrisonerId")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathMatching("/accounts.*"))
          .withQueryParam("reference", equalTo(testPrisonerId)),
      )
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))

      subAccountResponses.forEach { subAccount ->

        val expectedLog = GeneralLedgerDiscrepancyDetails(
          message = "Discrepancy found for prisoner $testPrisonerId",
          prisonerId = testPrisonerId,
          accountType = subAccount.reference,
          legacyAggregatedBalance = 1000L,
          generalLedgerBalance = 100L,
          discrepancy = 900L,
          glBreakdown = subAccountReturnedResponses,
          legacyBreakdown = listOf(
            PrisonerEstablishmentBalanceDetails(
              prisonId = "TES",
              accountCode = 2101,
              totalBalance = BigDecimal("10.00"),
              holdBalance = BigDecimal(0),
            ),
            PrisonerEstablishmentBalanceDetails(
              prisonId = "TES",
              accountCode = 2102,
              totalBalance = BigDecimal("10.00"),
              holdBalance = BigDecimal(0),
            ),
            PrisonerEstablishmentBalanceDetails(
              prisonId = "TES",
              accountCode = 2103,
              totalBalance = BigDecimal("10.00"),
              holdBalance = BigDecimal(0),
            ),
          ),
        )

        val logs = listAppender.list.map {
          it.formattedMessage + (it.throwableProxy?.let { proxy -> " " + proxy.message } ?: "")
        }
        assertThat(logs).anyMatch { it.contains(expectedLog.toString()) }
      }

      subAccountResponses.forEach { subAccount ->
        generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/sub-accounts/${subAccount.id}/balance")))
      }
    }

    @Test
    fun `should not show balance discrepancy for a prisoner when general ledger and legacy GL amounts match`() {
      // mock Internal Ledger
      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()

      val request = SyncOffenderTransactionRequest(
        transactionId = transactionId,
        requestId = UUID.randomUUID(),
        caseloadId = "TES",
        transactionTimestamp = timestamp,
        createdAt = timestamp,
        createdBy = "OMS_OWNER",
        createdByDisplayName = "OMS_OWNER",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = listOf(
          OffenderTransaction(
            entrySequence = 1,
            offenderId = 2607103,
            offenderDisplayId = testPrisonerId,
            offenderBookingId = 1227181,
            subAccountType = "SPND",
            postingType = "DR",
            type = "OT",
            description = "Sub-Account Transfer",
            amount = BigDecimal("30.0"),
            reference = null,
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, 1501, "DR", BigDecimal("30.0")),
              GeneralLedgerEntry(2, 2101, "CR", BigDecimal("10.0")),
              GeneralLedgerEntry(3, 2102, "CR", BigDecimal("10.0")),
              GeneralLedgerEntry(4, 2103, "CR", BigDecimal("10.0")),
            ),
          ),
        ),
      )

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      // mock GL
      val prisonerAccId = UUID.randomUUID()

      val subAccountResponses = accountMapping.prisonerSubAccounts.map { kv ->
        createSubAccountResponse(prisonerAccId, kv.key)
      }.toList()

      generalLedgerApi.stubGetAccount(testPrisonerId, prisonerAccId, subAccountResponses)

      val subAccountReturnedResponses = mutableListOf<SubAccountBalanceResponse>()

      subAccountResponses.forEach { subAccount ->
        subAccountReturnedResponses.add(generalLedgerApi.stubGetSubAccountBalance(subAccount.id, 1000))
      }

      webTestClient
        .get()
        .uri("/reconcile/prisoner-balances/$testPrisonerId")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathMatching("/accounts.*"))
          .withQueryParam("reference", equalTo(testPrisonerId)),
      )
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))

      val logs = listAppender.list.map {
        it.formattedMessage + (it.throwableProxy?.let { proxy -> " " + proxy.message } ?: "")
      }
      assertThat(logs).allMatch { it.contains("GeneralLedgerDiscrepancyDetails") == false }

      subAccountResponses.forEach { subAccount ->
        generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/sub-accounts/${subAccount.id}/balance")))
      }
    }

    @Test
    fun `should show balance discrepancy for a prisoner when general ledger when does not return sub accounts`() {
      // mock Internal Ledger
      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()

      val request = SyncOffenderTransactionRequest(
        transactionId = transactionId,
        requestId = UUID.randomUUID(),
        caseloadId = "TES",
        transactionTimestamp = timestamp,
        createdAt = timestamp,
        createdBy = "OMS_OWNER",
        createdByDisplayName = "OMS_OWNER",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = listOf(
          OffenderTransaction(
            entrySequence = 1,
            offenderId = 2607103,
            offenderDisplayId = testPrisonerId,
            offenderBookingId = 1227181,
            subAccountType = "SPND",
            postingType = "DR",
            type = "OT",
            description = "Sub-Account Transfer",
            amount = BigDecimal("30.0"),
            reference = null,
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, 1501, "DR", BigDecimal("30.0")),
              GeneralLedgerEntry(2, 2101, "CR", BigDecimal("10.0")),
              GeneralLedgerEntry(3, 2102, "CR", BigDecimal("10.0")),
              GeneralLedgerEntry(4, 2103, "CR", BigDecimal("10.0")),
            ),
          ),
        ),
      )

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      // mock GL
      val prisonerAccId = UUID.randomUUID()

      generalLedgerApi.stubGetAccount(testPrisonerId, prisonerAccId, emptyList())

      val subAccountReturnedResponses = mutableListOf<SubAccountBalanceResponse>()

      webTestClient
        .get()
        .uri("/reconcile/prisoner-balances/$testPrisonerId")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathMatching("/accounts.*"))
          .withQueryParam("reference", equalTo(testPrisonerId)),
      )
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))

      accountMapping.prisonerSubAccounts.keys.forEach { reference ->
        val expectedLog = GeneralLedgerDiscrepancyDetails(
          message = "Gl account not found for prisoner $testPrisonerId",
          prisonerId = testPrisonerId,
          accountType = reference,
          legacyAggregatedBalance = 1000L,
          generalLedgerBalance = 0L,
          discrepancy = 1000L,
          glBreakdown = subAccountReturnedResponses,
          legacyBreakdown = listOf(
            PrisonerEstablishmentBalanceDetails(
              prisonId = "TES",
              accountCode = 2101,
              totalBalance = BigDecimal("10.00"),
              holdBalance = BigDecimal(0),
            ),
            PrisonerEstablishmentBalanceDetails(
              prisonId = "TES",
              accountCode = 2102,
              totalBalance = BigDecimal("10.00"),
              holdBalance = BigDecimal(0),
            ),
            PrisonerEstablishmentBalanceDetails(
              prisonId = "TES",
              accountCode = 2103,
              totalBalance = BigDecimal("10.00"),
              holdBalance = BigDecimal(0),
            ),
          ),
        )

        val logs = listAppender.list.map {
          it.formattedMessage + (it.throwableProxy?.let { proxy -> " " + proxy.message } ?: "")
        }
        assertThat(logs).anyMatch { it.contains(expectedLog.toString()) }
      }

      generalLedgerApi.verify(0, getRequestedFor(urlPathMatching("/sub-accounts/.*/balance")))
    }

    @Test
    fun `should show balance discrepancy for a prisoner when general ledger when does not return parent account`() {
      // mock Internal Ledger
      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()

      val request = SyncOffenderTransactionRequest(
        transactionId = transactionId,
        requestId = UUID.randomUUID(),
        caseloadId = "TES",
        transactionTimestamp = timestamp,
        createdAt = timestamp,
        createdBy = "OMS_OWNER",
        createdByDisplayName = "OMS_OWNER",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = listOf(
          OffenderTransaction(
            entrySequence = 1,
            offenderId = 2607103,
            offenderDisplayId = testPrisonerId,
            offenderBookingId = 1227181,
            subAccountType = "SPND",
            postingType = "DR",
            type = "OT",
            description = "Sub-Account Transfer",
            amount = BigDecimal("30.0"),
            reference = null,
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, 1501, "DR", BigDecimal("30.0")),
              GeneralLedgerEntry(2, 2101, "CR", BigDecimal("10.0")),
              GeneralLedgerEntry(3, 2102, "CR", BigDecimal("10.0")),
              GeneralLedgerEntry(4, 2103, "CR", BigDecimal("10.0")),
            ),
          ),
        ),
      )

      webTestClient.post()
        .uri("/sync/offender-transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(objectMapper.writeValueAsString(request))
        .exchange()
        .expectStatus().isCreated

      // mock GL
      generalLedgerApi.stubGetAccountNotFound(testPrisonerId)

      val subAccountReturnedResponses = mutableListOf<SubAccountBalanceResponse>()

      webTestClient
        .get()
        .uri("/reconcile/prisoner-balances/$testPrisonerId")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathMatching("/accounts.*"))
          .withQueryParam("reference", equalTo(testPrisonerId)),
      )
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))

      accountMapping.prisonerSubAccounts.keys.forEach { reference ->
        val expectedLog = GeneralLedgerDiscrepancyDetails(
          message = "Gl account not found for prisoner $testPrisonerId",
          prisonerId = testPrisonerId,
          accountType = reference,
          legacyAggregatedBalance = 1000L,
          generalLedgerBalance = 0L,
          discrepancy = 1000L,
          glBreakdown = subAccountReturnedResponses,
          legacyBreakdown = listOf(
            PrisonerEstablishmentBalanceDetails(
              prisonId = "TES",
              accountCode = 2101,
              totalBalance = BigDecimal("10.00"),
              holdBalance = BigDecimal(0),
            ),
            PrisonerEstablishmentBalanceDetails(
              prisonId = "TES",
              accountCode = 2102,
              totalBalance = BigDecimal("10.00"),
              holdBalance = BigDecimal(0),
            ),
            PrisonerEstablishmentBalanceDetails(
              prisonId = "TES",
              accountCode = 2103,
              totalBalance = BigDecimal("10.00"),
              holdBalance = BigDecimal(0),
            ),
          ),
        )

        val logs = listAppender.list.map {
          it.formattedMessage + (it.throwableProxy?.let { proxy -> " " + proxy.message } ?: "")
        }
        assertThat(logs).anyMatch { it.contains(expectedLog.toString()) }
      }

      generalLedgerApi.verify(0, getRequestedFor(urlPathMatching("/sub-accounts/.*/balance")))
    }
  }
}
