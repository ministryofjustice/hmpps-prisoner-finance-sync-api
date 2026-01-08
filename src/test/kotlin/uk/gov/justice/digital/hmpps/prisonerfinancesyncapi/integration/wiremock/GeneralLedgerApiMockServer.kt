package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlSubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionReceipt
import java.util.UUID

class GeneralLedgerApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {

  companion object {
    val generalLedgerApi = GeneralLedgerApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    generalLedgerApi.start()
  }

  override fun afterAll(context: ExtensionContext) {
    generalLedgerApi.stop()
  }

  override fun beforeEach(context: ExtensionContext) {
    generalLedgerApi.resetAll()
  }
}

class GeneralLedgerApiMockServer :
  WireMockServer(
    WireMockConfiguration.wireMockConfig()
      .port(8091)
      .notifier(ConsoleNotifier(true)),
  ) {

  private val mapper = ObjectMapper()

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

  fun stubGetAccount(reference: String, returnUuid: String = UUID.randomUUID().toString()) {
    val response = GlAccountResponse(
      id = UUID.fromString(returnUuid),
      name = "Account for $reference",
      references = listOf(reference),
    )

    stubFor(
      get(urlPathEqualTo("/accounts"))
        .withQueryParam("reference", equalTo(reference))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(mapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetAccountNotFound(reference: String) {
    stubFor(
      get(urlPathEqualTo("/accounts"))
        .withQueryParam("reference", equalTo(reference))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(404),
        ),
    )
  }

  fun stubCreateAccount(reference: String, returnUuid: String = UUID.randomUUID().toString()) {
    val response = GlAccountResponse(
      id = UUID.fromString(returnUuid),
      name = "Created Account $reference",
      references = listOf(reference),
    )

    stubFor(
      post(urlEqualTo("/accounts"))
        .withRequestBody(matchingJsonPath("$.reference", equalTo(reference)))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(201)
            .withBody(mapper.writeValueAsString(response)),
        ),
    )
  }

  fun verifyCreateAccount(reference: String) {
    verify(
      postRequestedFor(urlEqualTo("/accounts"))
        .withRequestBody(matchingJsonPath("$.reference", equalTo(reference))),
    )
  }

  fun stubGetSubAccountNotFound(parentId: String, reference: String) {
    stubFor(
      get(urlPathEqualTo("/accounts/$parentId/sub-accounts"))
        .withQueryParam("reference", equalTo(reference)) // Query by reference
        .willReturn(aResponse().withStatus(404)),
    )
  }

  fun stubCreateSubAccount(parentId: String, reference: String, returnUuid: String = UUID.randomUUID().toString()) {
    val response = GlSubAccountResponse(
      id = UUID.fromString(returnUuid),
      parentAccountId = UUID.fromString(parentId),
      reference = reference,
    )

    stubFor(
      post(urlEqualTo("/accounts/$parentId/sub-accounts"))
        .withRequestBody(matchingJsonPath("$.reference", equalTo(reference))) // Body contains reference
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(201)
            .withBody(mapper.writeValueAsString(response)),
        ),
    )
  }

  fun verifyCreateSubAccount(parentId: String, reference: String) {
    verify(
      postRequestedFor(urlEqualTo("/accounts/$parentId/sub-accounts"))
        .withRequestBody(matchingJsonPath("$.reference", equalTo(reference))),
    )
  }

  fun stubPostTransaction(
    creditorUuid: String? = null,
    debtorUuid: String? = null,
  ) {
    val response = GlTransactionReceipt(id = UUID.randomUUID())

    var mapping = post(urlEqualTo("/transactions"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
          .withStatus(201)
          .withBody(mapper.writeValueAsString(response)),
      )

    if (creditorUuid != null) {
      mapping = mapping.withRequestBody(matchingJsonPath("$.creditorAccount", equalTo(creditorUuid)))
    }
    if (debtorUuid != null) {
      mapping = mapping.withRequestBody(matchingJsonPath("$.debtorAccount", equalTo(debtorUuid)))
    }

    stubFor(mapping)
  }
}
