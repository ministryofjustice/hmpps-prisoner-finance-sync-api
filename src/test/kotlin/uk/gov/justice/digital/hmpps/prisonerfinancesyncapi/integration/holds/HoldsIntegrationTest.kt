package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.holds

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HoldsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HoldsApiExtension.Companion.holdsApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class, HoldsApiExtension::class)
class HoldsIntegrationTest : IntegrationTestBase() {

  private val wiremockClient = WireMock(8092)

  @BeforeEach
  fun setup() {
    integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
  }

  @Nested
  @DisplayName("postHolds")
  inner class PostHolds {

    @Test
    fun `should return a 201 when a hold is created`() {
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
      )

      val timeConversionService = TimeConversionService()

      val expectedHoldRequest = CreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountRef = CreateHoldRequest.SubAccountRef.CASH,
        legacyHoldNumber = 123456789,
        createdAt = timeConversionService.toUtcInstant(syncHoldRequest.createdAt),
        createdBy = "USER",
        holdFromDate = timeConversionService.toUtcInstant(syncHoldRequest.holdFromDate),
        holdUntilDate = timeConversionService.toUtcInstant(syncHoldRequest.holdUntilDate as LocalDateTime),
        isReleased = false,
        description = "Test Hold",
        holdType = CreateHoldRequest.HoldType.WHF,
        holdLocation = "LEI",
        amount = syncHoldRequest.amount.toPence(),
      )

      holdsApi.stubPostHold(expectedHoldRequest)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isCreated
    }

    @Test
    fun `should return a 403 when using the incorrect role`() {
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
      )

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE__INCORRECT_ROLE")))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return a 409 when a hold already exists in the mapping table`() {
      val legacyHoldNumber = 123456789L
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23111",
        subAccountCode = 2101,
        holdNumber = legacyHoldNumber,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "HOA",
        holdLocation = "LEI",
        amount = BigDecimal("20"),
      )

      val timeConversionService = TimeConversionService()

      val expectedHoldRequest = CreateHoldRequest(
        prisonNumber = syncHoldRequest.prisonNumber,
        subAccountRef = CreateHoldRequest.SubAccountRef.CASH,
        legacyHoldNumber = syncHoldRequest.holdNumber,
        createdAt = timeConversionService.toUtcInstant(syncHoldRequest.createdAt),
        createdBy = syncHoldRequest.createdBy,
        holdFromDate = timeConversionService.toUtcInstant(syncHoldRequest.holdFromDate),
        holdUntilDate = timeConversionService.toUtcInstant(syncHoldRequest.holdUntilDate as LocalDateTime),
        isReleased = syncHoldRequest.isReleased,
        description = syncHoldRequest.description,
        holdType = CreateHoldRequest.HoldType.HOA,
        holdLocation = syncHoldRequest.holdLocation,
        amount = syncHoldRequest.amount.toPence(),
      )

      holdsApi.stubPostHold(expectedHoldRequest)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isEqualTo(201)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isEqualTo(409)

      wiremockClient.verifyThat(1, postRequestedFor(urlPathMatching("/holds")))
    }
  }
}
