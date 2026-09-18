package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence

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

class AdvancesApiMockServer :
  WireMockServer(
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

  fun stubPostAdvance(advanceRequest: SyncCreateAdvanceRecordRequest, advanceRecordResponse: AdvanceRecordResponse) {
    val timeConversionService = TimeConversionService()

    stubFor(
      post("/advances")
        .withRequestBody(matchingJsonPath("$.legacyPaymentProfileId", equalTo(advanceRequest.legacyPaymentProfileId)))
        .withRequestBody(matchingJsonPath("$.legacyInformationNumber", equalTo(advanceRequest.legacyInformationNumber)))
        .withRequestBody(matchingJsonPath("$.prisonNumber", equalTo(advanceRequest.prisonNumber)))
        .withRequestBody(matchingJsonPath("$.prisonID", equalTo(advanceRequest.prisonID)))
        .withRequestBody(matchingJsonPath("$.amount", equalTo(advanceRequest.amount.toPence().toString())))
        .withRequestBody(matchingJsonPath("$.createdOn", equalTo(timeConversionService.toUtcInstant(advanceRequest.createdOn).toString())))
        .withRequestBody(matchingJsonPath("$.repaymentStartDate", equalTo(timeConversionService.toUtcInstant(advanceRequest.repaymentStartDate).toString())))
        .withRequestBody(matchingJsonPath("$.repaymentAmount", equalTo(advanceRequest.repaymentAmount.toPence().toString())))
        .withRequestBody(matchingJsonPath("$.reference", equalTo(advanceRequest.reference)))
        .withRequestBody(matchingJsonPath("$.createdBy", equalTo(advanceRequest.createdBy)))
        .withRequestBody(matchingJsonPath("$.status", equalTo(advanceRequest.status.toString())))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(201)
            .withBody(mapper.writeValueAsString(advanceRecordResponse)),
        ),
    )
  }
}
