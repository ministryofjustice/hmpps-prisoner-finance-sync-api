package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.subscriptions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.SubscriptionsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.SubscriptionsApiExtension.Companion.subscriptionsApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SubscriptionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncCreateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncCreateSubscriptionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncUpdateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerIdempotencyService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class, SubscriptionsApiExtension::class)
class SubscriptionsIntegrationTest : IntegrationTestBase() {

  val generalLedgerIdempotencyService = GeneralLedgerIdempotencyService()
  val timeConversionService = TimeConversionService()

  @BeforeEach
  fun setup() {
    integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
    subscriptionsApi.resetAll()
  }

  @Nested
  inner class SyncSubscription {

    val legacyPaymentProfileId = 123456789L
    val entrySequence = 1
    val idempotencyKey = generalLedgerIdempotencyService.genTransactionIdempotencyKey(legacyPaymentProfileId, entrySequence)
    val syncCreateSubscriptionRequest = SyncCreateSubscriptionRequest(
      legacyPaymentProfileId = legacyPaymentProfileId,
      amount = BigDecimal(10.0),
      dayOfPayment = DayOfWeek.MONDAY,
      startDate = LocalDate.now(),
      endDate = LocalDate.now().plusWeeks(1),
      subscriptionType = "TELE",
      reference = "",
      createdBy = "USER",
      createdAt = LocalDateTime.now(),
      prisonId = "LEI",
      prisonNumber = "A2222AA",
      subAccountCode = 2101,
    )

    val subscriptionResponse = SubscriptionResponse(
      id = UUID.randomUUID(),
      legacyPaymentProfileId = syncCreateSubscriptionRequest.legacyPaymentProfileId,
      amount = syncCreateSubscriptionRequest.amount.toPence(),
      dayOfPayment = SubscriptionResponse.DayOfPayment.MONDAY,
      startDate = syncCreateSubscriptionRequest.startDate,
      subscriptionType = syncCreateSubscriptionRequest.subscriptionType,
      createdBy = syncCreateSubscriptionRequest.createdBy,
      createdAt = timeConversionService.toUtcInstant(syncCreateSubscriptionRequest.createdAt),
      prisonId = syncCreateSubscriptionRequest.prisonId,
      prisonNumber = syncCreateSubscriptionRequest.prisonNumber,
      subAccountRef = SubscriptionResponse.SubAccountRef.CASH,
      endDate = syncCreateSubscriptionRequest.endDate,
      reference = syncCreateSubscriptionRequest.reference,
    )

    @Test
    fun `should return a 201 when the sync subscription endpoint is called`() {
      subscriptionsApi.stubPostSubscription(idempotencyKey, subscriptionResponse)

      val response = webTestClient.post().uri("/sync/subscriptions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateSubscriptionRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CREATED)
        .expectBody<SyncCreateSubscriptionResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.subscriptionId).isEqualTo(subscriptionResponse.id)
    }

    @Test
    fun `should return a 201 when the same subscription is sent twice`() {
      subscriptionsApi.stubPostSubscription(idempotencyKey, subscriptionResponse)

      val responseOne = webTestClient.post().uri("/sync/subscriptions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateSubscriptionRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CREATED)
        .expectBody<SyncCreateSubscriptionResponse>()
        .returnResult()
        .responseBody!!

      val responseTwo = webTestClient.post().uri("/sync/subscriptions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateSubscriptionRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CREATED)
        .expectBody<SyncCreateSubscriptionResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseOne.subscriptionId).isEqualTo(responseTwo.subscriptionId)
    }

    @Test
    fun `should return a 400 when an invalid payload is sent`() {
      webTestClient
        .post()
        .uri("/sync/subscriptions")
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
        .uri("/sync/subscriptions")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE__INCORRECT_ROLE")))
        .bodyValue(syncCreateSubscriptionRequest)
        .exchange()
        .expectStatus().isForbidden
        .expectBody<ErrorResponse>()
    }

    @Test
    fun `should return a 502 when Advance service returns a 5XX error`() {
      subscriptionsApi.stubPostSubscriptionsReturnsError(500)

      webTestClient
        .post()
        .uri("/sync/subscriptions")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateSubscriptionRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
        .expectBody<ErrorResponse>()
    }
  }

  @Nested
  inner class UpdateSubscription {

    @Test
    fun `should return a 501 when the update subscription endpoint is called and not implemented`() {
      val subscriptionId = UUID.randomUUID()

      val syncUpdateSubscriptionRequest = SyncUpdateSubscriptionRequest(
        endDate = LocalDate.now(),
      )

      webTestClient.post().uri("/sync/subscriptions/$subscriptionId/update")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncUpdateSubscriptionRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }
  }
}
