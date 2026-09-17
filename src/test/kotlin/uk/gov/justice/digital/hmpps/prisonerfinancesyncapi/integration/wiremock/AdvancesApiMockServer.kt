package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest

class AdvancesApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {

  companion object {
    val advancesApi = AdvancesApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    advancesApi.start()
  }

  override fun afterAll(context: ExtensionContext) {
    advancesApi.stop()
  }

  override fun beforeEach(context: ExtensionContext) {
    advancesApi.resetAll()
  }
}

class AdvancesApiMockServer : WireMockServer(
  WireMockConfiguration.wireMockConfig()
    .port(8093)
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

  fun stubPostAdvance(request: CreateAdvanceRecordRequest, advanceRecordResponse: AdvanceRecordResponse) {

    val expectedRequestBody = mapper.writeValueAsString(request)

    stubFor(
      post("/advances")
        .withRequestBody(equalToJson(expectedRequestBody))
        .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(mapper.writeValueAsString(advanceRecordResponse)).withStatus(201)
      )
    )
  }
}