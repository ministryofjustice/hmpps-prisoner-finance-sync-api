package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SubscriptionResponse
import java.util.UUID

class SubscriptionsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {

  companion object {
    val subscriptionsApi = SubscriptionsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    subscriptionsApi.start()
  }

  override fun afterAll(context: ExtensionContext) {
    subscriptionsApi.stop()
  }

  override fun beforeEach(context: ExtensionContext) {
    subscriptionsApi.resetAll()
  }
}

class SubscriptionsApiMockServer :
  WireMockServer(
    WireMockConfiguration.wireMockConfig()
      .port(8094)
      .notifier(ConsoleNotifier(true)),
  ) {
  private val mapper = ObjectMapper().registerModule(JavaTimeModule())

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) """{"status":"UP"}""" else """{"status":"DOWN"}""")
          .withStatus(status),
      ),
    )
  }

  fun stubPostSubscription(expectedIdempotencyKey: UUID, response: SubscriptionResponse) {
    stubFor(
      post("/subscriptions")
        .withHeader("Idempotency-Key", equalTo(expectedIdempotencyKey.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(mapper.writeValueAsString(response))
            .withStatus(201),
        ),
    )
  }
}
