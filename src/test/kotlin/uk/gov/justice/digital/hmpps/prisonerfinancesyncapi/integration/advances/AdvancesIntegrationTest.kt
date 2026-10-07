package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.advances

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.AdvancesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.AdvancesApiExtension.Companion.advancesApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncAdvanceRepayResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncAdvanceWriteOffResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRepayRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceWriteOffRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.InMemoryAccountCache
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class, AdvancesApiExtension::class, GeneralLedgerApiExtension::class)
class AdvancesIntegrationTest : IntegrationTestBase() {

  val timeConversionService = TimeConversionService()

  @Autowired
  lateinit var requestCache: InMemoryAccountCache

  @Nested
  inner class PostAdvance {

    @BeforeEach
    fun setup() {
      integrationTestHelpers.clearDB()
      hmppsAuth.stubGrantToken()
      generalLedgerApi.resetAll()
      advancesApi.resetAll()
      requestCache.clear()
    }

    val prisonNumber = "A1234BC"
    val prisonId = "LEI"
    val prisonerAccountId = UUID.randomUUID()
    val prisonerSpendsAccount = UUID.randomUUID()
    val prisonAccountId = UUID.randomUUID()
    val prisonAdvanceAccount = UUID.randomUUID()

    val createdOn = LocalDateTime.now()
    val repaymentStartDate = LocalDateTime.now().plusDays(1)
    val syncCreateAdvanceRecordRequest = SyncCreateAdvanceRecordRequest(
      legacyPaymentProfileId = 1234,
      legacyInformationNumber = "5678",
      prisonNumber = prisonNumber,
      prisonID = prisonId,
      amount = BigDecimal("5.00"),
      createdOn = createdOn,
      repaymentStartDate = repaymentStartDate,
      repaymentAmount = BigDecimal("0.50"),
      reference = "REF",
      createdBy = "USER",
      status = CreateAdvanceRecordRequest.Status.ACTIVE,
      legacyTransactionId = 123,
    )

    @Nested
    inner class CreateAdvance {
      val advanceUuid = UUID.randomUUID()
      val stubbedSyncCreateAdvanceRecordResponse = SyncCreateAdvanceRecordResponse(
        paymentProfileId = syncCreateAdvanceRecordRequest.legacyPaymentProfileId,
        advanceId = advanceUuid,
      )

      @BeforeEach
      fun setup() {
        val stubbedAdvanceResponse = AdvanceRecordResponse(
          id = advanceUuid,
          legacyPaymentProfileId = 1234,
          legacyInformationNumber = "5678",
          prisonNumber = "A1234BC",
          prisonID = "LEI",
          amount = 500,
          createdOn = timeConversionService.toUtcInstant(createdOn as LocalDateTime),
          repaymentStartDate = timeConversionService.toUtcInstant(repaymentStartDate as LocalDateTime),
          repaymentAmount = 50,
          reference = "REF",
          createdBy = "USER",
          status = AdvanceRecordResponse.Status.ACTIVE,
        )

        advancesApi.stubPostAdvance(syncCreateAdvanceRecordRequest, stubbedAdvanceResponse)
      }

      private fun postAdvance() = webTestClient.post().uri("/sync/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRecordRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<SyncCreateAdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      @Test
      fun `should return a 201, resolve the GL accounts and then created advance`() {
        integrationTestHelpers.stubGlAccountsAndSubAccountsForPrisonerAndPrison(
          prisonParentAccount = prisonAccountId,
          prisonSubAccount = prisonAdvanceAccount,
          prisonSubAccountRef = "1502:ADV",
          prisonParentAccountRef = syncCreateAdvanceRecordRequest.prisonID,
          prisonerParentAccount = prisonerAccountId,
          prisonerSubAccount = prisonerSpendsAccount,
          prisonerSubAccountRef = "SPENDS",
          prisonerParentAccountRef = syncCreateAdvanceRecordRequest.prisonNumber,
        )

        val responseBody = postAdvance()

        assertThat(responseBody.paymentProfileId).isEqualTo(stubbedSyncCreateAdvanceRecordResponse.paymentProfileId)
        assertThat(responseBody.advanceId).isEqualTo(stubbedSyncCreateAdvanceRecordResponse.advanceId)

        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncCreateAdvanceRecordRequest.prisonID}")))
        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncCreateAdvanceRecordRequest.prisonNumber}")))

        generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts")))

        generalLedgerApi.verify(0, postRequestedFor(urlEqualTo("/accounts/$prisonerAccountId/sub-accounts")))
        generalLedgerApi.verify(0, postRequestedFor(urlEqualTo("/accounts/$prisonAccountId/sub-accounts")))
      }

      @Test
      fun `should return a 201, create the GL sub accounts and then created advance`() {
        integrationTestHelpers.stubCreateGlSubAccountsForPrisonerAndPrison(
          prisonParentAccount = prisonAccountId,
          prisonSubAccount = prisonAdvanceAccount,
          prisonSubAccountRef = "1502:ADV",
          prisonParentAccountRef = syncCreateAdvanceRecordRequest.prisonID,
          prisonerParentAccount = prisonerAccountId,
          prisonerSubAccount = prisonerSpendsAccount,
          prisonerSubAccountRef = "SPENDS",
          prisonerParentAccountRef = syncCreateAdvanceRecordRequest.prisonNumber,
        )

        val responseBody = postAdvance()

        assertThat(responseBody.paymentProfileId).isEqualTo(stubbedSyncCreateAdvanceRecordResponse.paymentProfileId)
        assertThat(responseBody.advanceId).isEqualTo(stubbedSyncCreateAdvanceRecordResponse.advanceId)

        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncCreateAdvanceRecordRequest.prisonID}")))
        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncCreateAdvanceRecordRequest.prisonNumber}")))

        generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts")))

        generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonerAccountId/sub-accounts")))
        generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonAccountId/sub-accounts")))
      }

      @Test
      fun `should return a 201, create the GL sub accounts, parent accounts, and then created advance`() {
        integrationTestHelpers.stubCreateGlParentAccountAndSubAccountForPrisonerAndPrison(
          prisonParentAccount = prisonAccountId,
          prisonSubAccount = prisonAdvanceAccount,
          prisonSubAccountRef = "1502:ADV",
          prisonParentAccountRef = syncCreateAdvanceRecordRequest.prisonID,
          prisonerParentAccount = prisonerAccountId,
          prisonerSubAccount = prisonerSpendsAccount,
          prisonerSubAccountRef = "SPENDS",
          prisonerParentAccountRef = syncCreateAdvanceRecordRequest.prisonNumber,
        )

        val responseBody = postAdvance()

        assertThat(responseBody.paymentProfileId).isEqualTo(stubbedSyncCreateAdvanceRecordResponse.paymentProfileId)
        assertThat(responseBody.advanceId).isEqualTo(stubbedSyncCreateAdvanceRecordResponse.advanceId)

        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncCreateAdvanceRecordRequest.prisonID}")))
        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncCreateAdvanceRecordRequest.prisonNumber}")))

        generalLedgerApi.verify(2, postRequestedFor(urlPathMatching("/accounts")))

        generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonerAccountId/sub-accounts")))
        generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonAccountId/sub-accounts")))
      }

      @Test
      fun `should return a 201 when an advance already exists in the mapping table`() {
        integrationTestHelpers.stubGlAccountsAndSubAccountsForPrisonerAndPrison(
          prisonParentAccount = prisonAccountId,
          prisonSubAccount = prisonAdvanceAccount,
          prisonSubAccountRef = "1502:ADV",
          prisonParentAccountRef = syncCreateAdvanceRecordRequest.prisonID,
          prisonerParentAccount = prisonerAccountId,
          prisonerSubAccount = prisonerSpendsAccount,
          prisonerSubAccountRef = "SPENDS",
          prisonerParentAccountRef = syncCreateAdvanceRecordRequest.prisonNumber,
        )

        postAdvance()
        postAdvance()

        advancesApi.verify(1, postRequestedFor(urlPathMatching("/advances")))
        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncCreateAdvanceRecordRequest.prisonID}")))
        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncCreateAdvanceRecordRequest.prisonNumber}")))

        generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts")))

        generalLedgerApi.verify(0, postRequestedFor(urlEqualTo("/accounts/$prisonerAccountId/sub-accounts")))
        generalLedgerApi.verify(0, postRequestedFor(urlEqualTo("/accounts/$prisonAccountId/sub-accounts")))
      }
    }

    @Test
    fun `should return a 400 when an invalid payload is sent`() {
      webTestClient
        .post()
        .uri("/sync/advances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(
          """
          {"hello" : "world" }
          """.trimIndent(),
        )
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ErrorResponse>()
    }

    @Test
    fun `should return a 403 when using the incorrect role`() {
      webTestClient
        .post()
        .uri("/sync/advances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE__INCORRECT_ROLE")))
        .bodyValue(syncCreateAdvanceRecordRequest)
        .exchange()
        .expectStatus().isForbidden
        .expectBody<ErrorResponse>()
    }

    @Test
    fun `should return a 502 when GL service returns a 5XX error`() {
      generalLedgerApi.stubGetAccountReturnsError(syncCreateAdvanceRecordRequest.prisonID)
      generalLedgerApi.stubGetAccountReturnsError(syncCreateAdvanceRecordRequest.prisonNumber)

      webTestClient
        .post()
        .uri("/sync/advances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRecordRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
        .expectBody<ErrorResponse>()
    }

    @Test
    fun `should return a 502 when Advance service returns a 5XX error`() {
      integrationTestHelpers.stubGlAccountsAndSubAccountsForPrisonerAndPrison(
        prisonParentAccount = prisonAccountId,
        prisonSubAccount = prisonAdvanceAccount,
        prisonSubAccountRef = "1502:ADV",
        prisonParentAccountRef = syncCreateAdvanceRecordRequest.prisonID,
        prisonerParentAccount = prisonerAccountId,
        prisonerSubAccount = prisonerSpendsAccount,
        prisonerSubAccountRef = "SPENDS",
        prisonerParentAccountRef = syncCreateAdvanceRecordRequest.prisonNumber,
      )

      advancesApi.stubPostAdvanceReturnsError(500)

      webTestClient
        .post()
        .uri("/sync/advances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRecordRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
        .expectBody<ErrorResponse>()
    }
  }

  @Nested
  inner class WriteOffAdvance {

    @BeforeEach
    fun setup() {
      integrationTestHelpers.clearDB()
      hmppsAuth.stubGrantToken()
      generalLedgerApi.resetAll()
      advancesApi.resetAll()
      requestCache.clear()
    }

    @Test
    fun `should return a 501 when the advance write-off endpoint is called and not implemented`() {
      val advanceId = UUID.randomUUID()

      val syncCreateAdvanceWriteOffRequest = SyncCreateAdvanceWriteOffRequest(
        writeOffDateTime = LocalDateTime.now(),
        writtenOffBy = "USER",
        amount = BigDecimal(10.0),
      )

      webTestClient.post().uri("/sync/advances/{advanceId}/write-off", advanceId)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceWriteOffRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_IMPLEMENTED)
        .expectBody<SyncAdvanceWriteOffResponse>()
        .returnResult()
        .responseBody!!
    }

    @Test
    fun `should return a 403 when when wrong role is provided`() {
      val advanceId = UUID.randomUUID()

      val syncCreateAdvanceWriteOffRequest = SyncCreateAdvanceWriteOffRequest(
        writeOffDateTime = LocalDateTime.now(),
        writtenOffBy = "USER",
        amount = BigDecimal(10.0),
      )

      webTestClient.post().uri("/sync/advances/{advanceId}/write-off", advanceId)
        .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
        .bodyValue(syncCreateAdvanceWriteOffRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
    }
  }

  @Nested
  inner class RepayAdvance {

    @BeforeEach
    fun setup() {
      integrationTestHelpers.clearDB()
      hmppsAuth.stubGrantToken()
      generalLedgerApi.resetAll()
      advancesApi.resetAll()
      requestCache.clear()
    }

    @Test
    fun `should return a 501 when the advance repay endpoint is called and not implemented`() {
      val advanceId = UUID.randomUUID()

      val syncCreateAdvanceRepayRequest = SyncCreateAdvanceRepayRequest(
        amount = BigDecimal(10.0),
        transactionId = 123456,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
      )

      webTestClient.post().uri("/sync/advances/{advanceId}/repay", advanceId)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRepayRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_IMPLEMENTED)
        .expectBody<SyncAdvanceRepayResponse>()
        .returnResult()
        .responseBody!!
    }
  }

  @Nested
  inner class MigrateAdvance {

    @BeforeEach
    fun setup() {
      integrationTestHelpers.clearDB()
      hmppsAuth.stubGrantToken()
      generalLedgerApi.resetAll()
      advancesApi.resetAll()
      requestCache.clear()
    }

    @Test
    fun `should return a 501 when the advance migrate endpoint is called and not implemented`() {
      val syncCreateAdvanceRepayRequest = SyncCreateAdvanceRecordRequest(
        legacyPaymentProfileId = 1,
        legacyInformationNumber = "1-1",
        prisonNumber = "A1234AA",
        prisonID = "LEI",
        createdOn = LocalDateTime.now(),
        createdBy = "USER",
        repaymentStartDate = LocalDateTime.now(),
        repaymentAmount = BigDecimal(10.0),
        reference = "SPENDS",
        comment = "You owe me money",
        status = CreateAdvanceRecordRequest.Status.ACTIVE,
        legacyTransactionId = 12345L,
        amount = BigDecimal(10.0),
      )

      webTestClient.post().uri("/migrate/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRepayRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_IMPLEMENTED)
        .expectBody<SyncCreateAdvanceRecordResponse>()
        .returnResult()
        .responseBody!!
    }
  }
}
