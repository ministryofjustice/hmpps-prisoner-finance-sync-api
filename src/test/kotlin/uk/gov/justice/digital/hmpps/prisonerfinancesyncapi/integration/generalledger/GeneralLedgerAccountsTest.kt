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
  fun `should lookup and create prisoner SUB accounts`() {
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
    generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts.*")))
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))
  }

  @Test
  fun `should lookup and create prison SUB accounts`() {
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
    generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts.*")))
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))
  }

  @Test
  fun `should lookup prison SUB accounts`() {
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
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts.*")))
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))
  }

  @Test
  fun `should lookup prisoner SUB accounts`() {
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
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts.*")))
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))
  }

  @Test
  fun `should lookup prison and prisoner account and not create a new one if it exists`() {
    val request = createRequest(testPrisonerId, "TES")

    generalLedgerApi.stubGetAccount(testPrisonerId)
    generalLedgerApi.stubGetAccount(request.caseloadId)

    webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isCreated

    generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts.*")))
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts.*")))
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))
  }

  @Test
  fun `should call general ledger to lookup an account and create it if not exists`() {
    val request = createRequest(testPrisonerId, "TES")

    generalLedgerApi.stubGetAccountNotFound(testPrisonerId)
    generalLedgerApi.stubGetAccountNotFound(request.caseloadId)
    generalLedgerApi.stubCreateAccount(testPrisonerId)
    generalLedgerApi.stubCreateAccount(request.caseloadId)

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
    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))
  }

  @Test
  fun `should not post any transaction`() {
    val request = createRequest(testPrisonerId)

    webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isCreated

    generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/transactions.*")))
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
}
