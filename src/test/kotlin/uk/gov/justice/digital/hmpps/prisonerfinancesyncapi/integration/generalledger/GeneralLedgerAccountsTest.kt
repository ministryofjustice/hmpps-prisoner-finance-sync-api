package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.generalledger

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
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
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.LedgerAccountMappingService
import java.math.BigDecimal
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

  private val testPrisonerId = "A1234AA"

  @Autowired
  private lateinit var accountMapping: LedgerAccountMappingService

  @BeforeEach
  fun setup() {
    generalLedgerApi.resetAll()
    hmppsAuth.stubGrantToken()
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
        amount = 10.00,
        reference = "REF",
        generalLedgerEntries = listOf(
          GeneralLedgerEntry(1, 1501, "DR", 10.00),
          GeneralLedgerEntry(2, 2102, "CR", 10.00),
        ),
      )
    val request = createRequest(testPrisonerId, "TES", listOf(transaction))

    val prisonerAccId = UUID.randomUUID()
    val prisonAccId = UUID.randomUUID()

    generalLedgerApi.stubGetAccount(testPrisonerId, prisonerAccId)
    generalLedgerApi.stubGetAccount(request.caseloadId, prisonAccId)

    val prisonRef = accountMapping.mapPrisonSubAccount(
      transaction.generalLedgerEntries[0].code,
      request.offenderTransactions[0].type,
    )
    val prisonerRef = accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code)

    generalLedgerApi.stubGetSubAccount(
      request.caseloadId,
      prisonRef,
    )

    generalLedgerApi.stubGetSubAccountNotFound(
      testPrisonerId,
      prisonerRef,
    )

    generalLedgerApi.stubCreateSubAccount(prisonerAccId, prisonerRef)

    webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isCreated

    generalLedgerApi.verify(
      1,
      getRequestedFor(
        urlPathMatching("/sub-accounts"),
      )
        .withQueryParam("accountReference", equalTo(request.caseloadId))
        .withQueryParam("reference", equalTo(prisonRef)),
    )

    generalLedgerApi.verify(
      1,
      getRequestedFor(
        urlPathMatching("/sub-accounts"),

      )
        .withQueryParam("accountReference", equalTo(testPrisonerId))
        .withQueryParam("reference", equalTo(prisonerRef)),
    )

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
        amount = 10.00,
        reference = "REF",
        generalLedgerEntries = listOf(
          GeneralLedgerEntry(1, 1501, "DR", 10.00),
          GeneralLedgerEntry(2, 2103, "CR", 10.00),
        ),
      )
    val request = createRequest(testPrisonerId, "TES", listOf(transaction))

    val prisonerAccId = UUID.randomUUID()
    val prisonAccId = UUID.randomUUID()

    generalLedgerApi.stubGetAccount(testPrisonerId, prisonerAccId)
    generalLedgerApi.stubGetAccount(request.caseloadId, prisonAccId)

    val prisonRef = accountMapping.mapPrisonSubAccount(
      transaction.generalLedgerEntries[0].code,
      request.offenderTransactions[0].type,
    )
    val prisonerRef = accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code)

    generalLedgerApi.stubGetSubAccountNotFound(
      request.caseloadId,
      prisonRef,
    )

    generalLedgerApi.stubGetSubAccount(
      testPrisonerId,
      prisonerRef,
    )

    generalLedgerApi.stubCreateSubAccount(prisonAccId, prisonRef)

    webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isCreated

    generalLedgerApi.verify(
      1,
      getRequestedFor(
        urlPathMatching("/sub-accounts"),
      )
        .withQueryParam("accountReference", equalTo(request.caseloadId))
        .withQueryParam("reference", equalTo(prisonRef)),
    )

    generalLedgerApi.verify(
      1,
      getRequestedFor(
        urlPathMatching("/sub-accounts"),

      )
        .withQueryParam("accountReference", equalTo(testPrisonerId))
        .withQueryParam("reference", equalTo(prisonerRef)),
    )

    generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
    generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
    generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
  }

  @Test
  fun `should find existing prison SUB account and not create new one`() {
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
        amount = 10.00,
        reference = "REF",
        generalLedgerEntries = listOf(
          GeneralLedgerEntry(1, 1501, "DR", 10.00),
          GeneralLedgerEntry(2, 2101, "CR", 10.00),
        ),
      )
    val request = createRequest(testPrisonerId, "TES", listOf(transaction))

    val prisonerAccId = UUID.randomUUID()
    val prisonAccId = UUID.randomUUID()

    generalLedgerApi.stubGetAccount(testPrisonerId, prisonerAccId)
    generalLedgerApi.stubGetAccount(request.caseloadId, prisonAccId)

    val prisonRef = accountMapping.mapPrisonSubAccount(
      transaction.generalLedgerEntries[0].code,
      request.offenderTransactions[0].type,
    )
    val prisonerRef = accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code)

    generalLedgerApi.stubGetSubAccount(
      request.caseloadId,
      prisonRef,
    )

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

    generalLedgerApi.verify(
      1,
      getRequestedFor(
        urlPathMatching("/sub-accounts"),
      )
        .withQueryParam("accountReference", equalTo(request.caseloadId))
        .withQueryParam("reference", equalTo(prisonRef)),
    )

    generalLedgerApi.verify(
      1,
      getRequestedFor(
        urlPathMatching("/sub-accounts"),

      )
        .withQueryParam("accountReference", equalTo(testPrisonerId))
        .withQueryParam("reference", equalTo(prisonerRef)),
    )

    generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
    generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
  }

  @Test
  fun `should find existing prisoner SUB accounts and not create new one`() {
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
        amount = 10.00,
        reference = "REF",
        generalLedgerEntries = listOf(
          GeneralLedgerEntry(1, 2102, "DR", 10.00),
          GeneralLedgerEntry(2, 2101, "CR", 10.00),
        ),
      )
    val request = createRequest(testPrisonerId, "TES", listOf(transaction))

    val prisonerAccId = UUID.randomUUID()

    generalLedgerApi.stubGetAccount(testPrisonerId, prisonerAccId)
    generalLedgerApi.stubGetAccount(request.caseloadId)

    generalLedgerApi.stubGetSubAccount(
      testPrisonerId,
      accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[0].code),
    )

    generalLedgerApi.stubGetSubAccount(
      testPrisonerId,
      accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code),
    )

    webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isCreated

    generalLedgerApi.verify(
      2,
      getRequestedFor(
        urlPathMatching("/sub-accounts"),

      )
        .withQueryParam("accountReference", equalTo(testPrisonerId))
        .withQueryParam("reference", matching(".*")),
    )

    generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/sub-accounts.*")))

    generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
    generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
  }

  @Test
  fun `should lookup prison and prisoner account and not create a new one if it exists`() {
    val prisonId = "TES"
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
        amount = 10.00,
        reference = "REF",
        generalLedgerEntries = listOf(
          GeneralLedgerEntry(1, 1501, "DR", 10.00),
          GeneralLedgerEntry(2, 2101, "CR", 10.00),
        ),
      )
    val request = createRequest(testPrisonerId, prisonId, listOf(transaction))

    generalLedgerApi.stubGetAccount(testPrisonerId)
    generalLedgerApi.stubGetAccount(request.caseloadId)

    generalLedgerApi.stubGetSubAccount(
      prisonId,
      accountMapping.mapPrisonSubAccount(
        transaction.generalLedgerEntries[0].code,
        transaction.type,
      ),
    )

    generalLedgerApi.stubGetSubAccount(
      testPrisonerId,
      accountMapping.mapPrisonerSubAccount(transaction.generalLedgerEntries[1].code),
    )

    webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isCreated

    generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts/.*/sub-accounts.*")))
    generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/sub-accounts.*")))
    generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
  }

  @Test
  fun `should call general ledger to lookup an account and create it if not exists`() {
    val request = createRequest(testPrisonerId, "TES")

    generalLedgerApi.stubGetAccountNotFound(testPrisonerId)
    generalLedgerApi.stubGetAccountNotFound(request.caseloadId)
    generalLedgerApi.stubCreateAccount(testPrisonerId)
    generalLedgerApi.stubCreateAccount(request.caseloadId)

    generalLedgerApi.stubGetSubAccount(
      testPrisonerId,
      accountMapping.mapPrisonerSubAccount(request.offenderTransactions[0].generalLedgerEntries[0].code),
    )

    generalLedgerApi.stubGetSubAccount(
      testPrisonerId,
      accountMapping.mapPrisonerSubAccount(request.offenderTransactions[0].generalLedgerEntries[1].code),
    )

    webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isCreated

    generalLedgerApi.verify(
      getRequestedFor(urlPathEqualTo("/accounts"))
        .withQueryParam("reference", com.github.tomakehurst.wiremock.client.WireMock.equalTo("TES")),
    )

    generalLedgerApi.verify(
      getRequestedFor(urlPathEqualTo("/accounts"))
        .withQueryParam("reference", com.github.tomakehurst.wiremock.client.WireMock.equalTo(testPrisonerId)),
    )

    generalLedgerApi.verify(2, postRequestedFor(urlPathMatching("/accounts.*")))
    generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/transactions.*")))
  }

  @Test
  fun `should successfully sync to internal ledger when general ledger is down`() {
    generalLedgerApi.stubFor(
      get(urlPathEqualTo("/accounts"))
        .withQueryParam("reference", com.github.tomakehurst.wiremock.client.WireMock.equalTo(testPrisonerId))
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
        amount = 10.00,
        reference = "REF",
        generalLedgerEntries = listOf(
          GeneralLedgerEntry(1, 2102, "DR", 10.00),
          GeneralLedgerEntry(2, 2101, "CR", 10.00),
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
    generalLedgerApi.stubCreateAccount(testPrisonerId, prisonerParentUuid.toString())
    generalLedgerApi.stubGetSubAccountNotFound(testPrisonerId, prisonerSubRef)
    generalLedgerApi.stubCreateSubAccount(prisonerParentUuid, prisonerSubRef, prisonerSubUuid)

    generalLedgerApi.stubGetAccountNotFound(prisonId)
    generalLedgerApi.stubCreateAccount(prisonId, prisonParentUuid.toString())
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
          amount = amount.toDouble(),
          reference = "REF-$transactionId",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 2102, "CR", amount.toDouble()),
            GeneralLedgerEntry(2, 1502, "DR", amount.toDouble()),
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
    generalLedgerApi.stubCreateAccount(prisoner1, prisoner1ParentUuid.toString())
    generalLedgerApi.stubGetSubAccountNotFound(prisoner1, spendsSubRef)
    generalLedgerApi.stubCreateSubAccount(prisoner1ParentUuid, spendsSubRef, prisoner1SubUuid)

    generalLedgerApi.stubGetAccountNotFound(prisoner2)
    generalLedgerApi.stubCreateAccount(prisoner2, prisoner2ParentUuid.toString())
    generalLedgerApi.stubGetSubAccountNotFound(prisoner2, spendsSubRef)
    generalLedgerApi.stubCreateSubAccount(prisoner2ParentUuid, spendsSubRef, prisoner2SubUuid)

    generalLedgerApi.stubGetAccountNotFound(prisonId)
    generalLedgerApi.stubCreateAccount(prisonId, prisonParentUuid.toString())
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
          amount = amount1.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 2102, "DR", amount1.toDouble()),
            GeneralLedgerEntry(2, 2501, "CR", amount1.toDouble()),
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
          amount = amount2.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 2102, "DR", amount2.toDouble()),
            GeneralLedgerEntry(2, 2501, "CR", amount2.toDouble()),
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

    // TODO uncomment this once we remove the prisoner filter
    // generalLedgerApi.verifyCreateAccount(prisoner2)

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
    generalLedgerApi.stubCreateAccount(testPrisonerId, prisonerParentUuid.toString())

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
          amount = amount.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 2102, "DR", amount.toDouble()),
            GeneralLedgerEntry(2, 2101, "CR", amount.toDouble()),
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
