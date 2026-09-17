package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.advances

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.AdvancesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.AdvancesApiExtension.Companion.advancesApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID


@ExtendWith(MockitoExtension::class, AdvancesApiExtension::class)
class AdvancesIntegrationTest : IntegrationTestBase() {

  @Nested
  inner class PostAdvance {

    @Test
    fun `should return 201 and the created advance`() {

    val syncCreateAdvanceRecordRequest = SyncCreateAdvanceRecordRequest(
      legacyPaymentProfileId = "1234",
      legacyInformationNumber = "5678",
      prisonNumber = "A1234BC",
      prisonID = "LEI",
      amount = BigDecimal("5.00"),
      createdOn = Instant.now(),
      repaymentStartDate = Instant.now(),
      repaymentAmount = BigDecimal("0.50"),
      reference = "REF",
      createdBy = "USER",
      status = CreateAdvanceRecordRequest.Status.ACTIVE,
    )

      val expectedRequest = CreateAdvanceRecordRequest(
        legacyPaymentProfileId = "1234",
        legacyInformationNumber = "5678",
        prisonNumber = "A1234BC",
        prisonID = "LEI",
        amount = 500,
        createdOn = Instant.now(),
        repaymentStartDate = Instant.now(),
        repaymentAmount = 50,
        reference = "REF",
        createdBy = "USER",
        status = CreateAdvanceRecordRequest.Status.ACTIVE)

      val stubbedResponse = AdvanceRecordResponse(
        id = UUID.randomUUID(),
        legacyPaymentProfileId = "1234",
        legacyInformationNumber = "5678",
        prisonNumber = "A1234BC",
        prisonID = "LEI",
        amount = 500,
        createdOn = Instant.now(),
        repaymentStartDate = Instant.now(),
        repaymentAmount = 50,
        reference = "REF",
        createdBy = "USER",
        status = AdvanceRecordResponse.Status.ACTIVE,
      )

      advancesApi.stubPostAdvance(expectedRequest, stubbedResponse)

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
  }
}