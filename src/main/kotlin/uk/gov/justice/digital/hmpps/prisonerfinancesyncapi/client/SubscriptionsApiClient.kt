package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.subscriptions.SubscriptionRecordsApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.CreateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SubscriptionResponse
import java.util.UUID

@Component
class SubscriptionsApiClient(
  private val subscriptionRecordsApi: SubscriptionRecordsApi,

) : ApiClientBase("Subscriptions API") {

  @Throws(WebClientResponseException::class, IllegalStateException::class)
  fun postSubscription(request: CreateSubscriptionRequest, idempotencyKey: UUID): SubscriptionResponse {
    log.info("Creating subscription for payment profile id ${request.legacyPaymentProfileId} for prison number ${request.prisonNumber}")
    val response = handleExceptions(
      block = {
        subscriptionRecordsApi.createSubscriptionRecord(idempotencyKey, request)
          .block()
      },
    )

    return response
      ?: throw IllegalStateException("Received null response when creating subscription ${request.legacyPaymentProfileId}")
  }
}
