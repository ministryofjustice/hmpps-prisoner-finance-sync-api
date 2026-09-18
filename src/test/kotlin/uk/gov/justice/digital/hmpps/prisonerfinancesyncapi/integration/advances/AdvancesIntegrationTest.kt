package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.advances

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.AdvancesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.AdvancesApiExtension.Companion.advancesApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HoldsApiExtension.Companion.holdsApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class, AdvancesApiExtension::class)
class AdvancesIntegrationTest() : IntegrationTestBase() {

  val timeConversionService = TimeConversionService()
  private val wiremockClient = WireMock(8093)

  @Nested
  inner class PostAdvance {

    @BeforeEach
    fun setup() {
      integrationTestHelpers.clearDB()
      hmppsAuth.stubGrantToken()
    }

    @Test
    fun `should return 201 and the created advance`() {

    val createdOn = LocalDateTime.now()
    val repaymentStartDate = LocalDateTime.now().plusDays(1)

    val syncCreateAdvanceRecordRequest = SyncCreateAdvanceRecordRequest(
      legacyPaymentProfileId = "1234",
      legacyInformationNumber = "5678",
      prisonNumber = "A1234BC",
      prisonID = "LEI",
      amount = BigDecimal("5.00"),
      createdOn = createdOn,
      repaymentStartDate = repaymentStartDate,
      repaymentAmount = BigDecimal("0.50"),
      reference = "REF",
      createdBy = "USER",
      status = CreateAdvanceRecordRequest.Status.ACTIVE,
    )

      val stubbedResponse = AdvanceRecordResponse(
        id = UUID.randomUUID(),
        legacyPaymentProfileId = "1234",
        legacyInformationNumber = "5678",
        prisonNumber = "A1234BC",
        prisonID = "LEI",
        amount = 500,
        createdOn = timeConversionService.toUtcInstant(createdOn),
        repaymentStartDate = timeConversionService.toUtcInstant(repaymentStartDate),
        repaymentAmount = 50,
        reference = "REF",
        createdBy = "USER",
        status = AdvanceRecordResponse.Status.ACTIVE,
      )

      advancesApi.stubPostAdvance(syncCreateAdvanceRecordRequest, stubbedResponse)

      val responseBody = webTestClient.post().uri("/sync/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRecordRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<AdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody).isEqualTo(stubbedResponse)

    }

    @Test
    fun `should return a 403 when using the incorrect role`() {

      val createdOn = LocalDateTime.now()
      val repaymentStartDate = LocalDateTime.now().plusDays(1)

      val syncCreateAdvanceRecordRequest = SyncCreateAdvanceRecordRequest(
        legacyPaymentProfileId = "1234",
        legacyInformationNumber = "5678",
        prisonNumber = "A1234BC",
        prisonID = "LEI",
        amount = BigDecimal("5.00"),
        createdOn = createdOn,
        repaymentStartDate = repaymentStartDate,
        repaymentAmount = BigDecimal("0.50"),
        reference = "REF",
        createdBy = "USER",
        status = CreateAdvanceRecordRequest.Status.ACTIVE,
      )

      webTestClient
        .post()
        .uri("/sync/advances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE__INCORRECT_ROLE")))
        .bodyValue(syncCreateAdvanceRecordRequest)
        .exchange()
        .expectStatus().isForbidden
    }


    @Test
    fun `should return a 409 when a advance already exists in the mapping table`() {

      val createdOn = LocalDateTime.now()
      val repaymentStartDate = LocalDateTime.now().plusDays(1)

      val syncCreateAdvanceRecordRequest = SyncCreateAdvanceRecordRequest(
        legacyPaymentProfileId = "1234",
        legacyInformationNumber = "5678",
        prisonNumber = "A1234BC",
        prisonID = "LEI",
        amount = BigDecimal("500"),
        createdOn = createdOn,
        repaymentStartDate = repaymentStartDate,
        repaymentAmount = BigDecimal("50"),
        reference = "REF",
        createdBy = "USER",
        status = CreateAdvanceRecordRequest.Status.ACTIVE)

      val stubbedAdvanceResponse = AdvanceRecordResponse(
        id = UUID.randomUUID(),
        legacyPaymentProfileId = "1234",
        legacyInformationNumber = "5678",
        prisonNumber = "A1234BC",
        prisonID = "LEI",
        amount = 500,
        createdOn = timeConversionService.toUtcInstant(createdOn),
        repaymentStartDate = timeConversionService.toUtcInstant(repaymentStartDate),
        repaymentAmount = 50,
        reference = "REF",
        createdBy = "USER",
        status = AdvanceRecordResponse.Status.ACTIVE,
      )

      advancesApi.stubPostAdvance(syncCreateAdvanceRecordRequest, stubbedAdvanceResponse)

     webTestClient.post().uri("/sync/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRecordRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<AdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      webTestClient.post().uri("/sync/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRecordRequest)
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody<AdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      wiremockClient.verifyThat(1, postRequestedFor(urlPathMatching("/advances")))
    }
  }
}