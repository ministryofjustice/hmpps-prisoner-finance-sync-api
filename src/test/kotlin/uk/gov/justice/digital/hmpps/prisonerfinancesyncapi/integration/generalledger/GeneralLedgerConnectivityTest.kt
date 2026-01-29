package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.generalledger

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
class GeneralLedgerConnectivityTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val testPrisonerId = "A1234AA"

  @BeforeEach
  fun setup() {
    generalLedgerApi.resetAll()
    hmppsAuth.stubGrantToken()
  }

  @Test
  fun `should call general ledger to lookup an account and create it if not exists`() {
    generalLedgerApi.stubGetAccountNotFound(testPrisonerId)

    val request = createRequest(testPrisonerId)

    webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isCreated

    generalLedgerApi.verify(
      getRequestedFor(urlPathEqualTo("/accounts"))
        .withQueryParam("reference", com.github.tomakehurst.wiremock.client.WireMock.equalTo(testPrisonerId)),
    )

    generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts.*")))
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

  private fun createRequest(offenderId: String): SyncOffenderTransactionRequest {
    val randomTxId = Random.nextLong(100000, 999999)

    return SyncOffenderTransactionRequest(
      transactionId = randomTxId,
      requestId = UUID.randomUUID(),
      caseloadId = "MDI",
      transactionTimestamp = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      createdBy = "TEST",
      createdByDisplayName = "Test User",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      offenderTransactions = listOf(
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
    )
  }
}
