package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.HoldResponse
import java.util.UUID

class HoldsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {

  companion object {
    val holdsApi = HoldsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    holdsApi.start()
  }

  override fun afterAll(context: ExtensionContext) {
    holdsApi.stop()
  }

  override fun beforeEach(context: ExtensionContext) {
    holdsApi.resetAll()
  }
}

class HoldsApiMockServer :
  WireMockServer(
    WireMockConfiguration.wireMockConfig()
      .port(8092)
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

  fun stubPostHold(holdRequest: CreateHoldRequest) {
    val holdResponse = HoldResponse(
      id = UUID.randomUUID(),
      prisonNumber = holdRequest.prisonNumber,
      legacyHoldNumber = holdRequest.legacyHoldNumber,
      subAccountRef = HoldResponse.SubAccountRef.valueOf(holdRequest.subAccountRef.toString()),
      createdAt = holdRequest.createdAt,
      createdBy = holdRequest.createdBy,
      holdFromDate = holdRequest.holdFromDate,
      isReleased = holdRequest.isReleased,
      holdType = HoldResponse.HoldType.valueOf(holdRequest.holdType.toString()),
      amount = holdRequest.amount,
      holdLocation = holdRequest.holdLocation,
      holdUntilDate = holdRequest.holdUntilDate,
      description = holdRequest.description,
    )

    stubFor(
      post(urlPathEqualTo("/holds"))
        .withRequestBody(matchingJsonPath("$.prisonNumber", equalTo(holdRequest.prisonNumber)))
        .withRequestBody(matchingJsonPath("$.legacyHoldNumber", equalTo(holdRequest.legacyHoldNumber.toString())))
        .withRequestBody(matchingJsonPath("$.subAccountRef", equalTo(holdRequest.subAccountRef.toString())))
        .withRequestBody(matchingJsonPath("$.createdAt", equalTo(holdRequest.createdAt.toString())))
        .withRequestBody(matchingJsonPath("$.createdBy", equalTo(holdRequest.createdBy)))
        .withRequestBody(matchingJsonPath("$.holdFromDate", equalTo(holdRequest.holdFromDate.toString())))
        .withRequestBody(matchingJsonPath("$.isReleased", equalTo(holdRequest.isReleased.toString())))
        .withRequestBody(matchingJsonPath("$.holdType", equalTo(holdRequest.holdType.toString())))
        .withRequestBody(matchingJsonPath("$.amount", equalTo(holdRequest.amount.toString())))
        .withRequestBody(matchingJsonPath("$.holdLocation", equalTo(holdRequest.holdLocation)))
        .withRequestBody(matchingJsonPath("$.holdUntilDate", equalTo(holdRequest.holdUntilDate.toString())))
        .withRequestBody(matchingJsonPath("$.description", equalTo(holdRequest.description)))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(201)
            .withBody(mapper.writeValueAsString(holdResponse)),
        ),
    )
  }
}
