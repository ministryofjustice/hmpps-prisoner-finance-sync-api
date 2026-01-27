package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
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
import java.time.LocalDateTime
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

  // GET /accounts?reference={ref} -> Returns List<Account>
  fun stubGetAccount(reference: String, returnUuid: String = UUID.randomUUID().toString()) {
    val response = GlAccountResponse(
      id = UUID.fromString(returnUuid),
      reference = reference,
      createdAt = LocalDateTime.now(),
      createdBy = "MOCK_USER",
      subAccounts = emptyList(),
    )

    stubFor(
      get(urlPathEqualTo("/accounts"))
        .withQueryParam("reference", equalTo(reference))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(mapper.writeValueAsString(listOf(response))),
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
            .withStatus(200)
            .withBody("[]"),
        ),
    )
  }

  // POST /accounts -> Returns Single Account
  fun stubCreateAccount(reference: String, returnUuid: String = UUID.randomUUID().toString()) {
    val response = GlAccountResponse(
      id = UUID.fromString(returnUuid),
      reference = reference,
      createdAt = LocalDateTime.now(),
      createdBy = "MOCK_USER",
    )

    stubFor(
      post(urlEqualTo("/accounts"))
        .withRequestBody(matchingJsonPath("$.accountReference", equalTo(reference)))
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
        .withRequestBody(matchingJsonPath("$.accountReference", equalTo(reference))),
    )
  }

  fun stubGetSubAccountNotFound(parentReference: String, subAccountReference: String) {
    stubFor(
      get(urlPathEqualTo("/sub-accounts"))
        .withQueryParam("reference", equalTo(subAccountReference))
        .withQueryParam("accountReference", equalTo(parentReference))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody("[]"),
        ),
    )
  }

  // POST /accounts/{uuid}/sub-accounts -> Returns Single SubAccount
  fun stubCreateSubAccount(parentId: String, reference: String, returnUuid: String = UUID.randomUUID().toString()) {
    val response = GlSubAccountResponse(
      id = UUID.fromString(returnUuid),
      parentAccountId = UUID.fromString(parentId),
      reference = reference,
      createdAt = LocalDateTime.now(),
      createdBy = "MOCK_USER",
    )

    stubFor(
      post(urlEqualTo("/accounts/$parentId/sub-accounts"))
        .withRequestBody(matchingJsonPath("$.subAccountReference", equalTo(reference)))
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
        .withRequestBody(matchingJsonPath("$.subAccountReference", equalTo(reference))),
    )
  }

  // POST /transactions
  fun stubPostTransaction(
    creditorSubAccountUuid: String? = null,
    debtorSubAccountUuid: String? = null,
    reference: String? = null,
  ) {
    val response = GlTransactionReceipt(
      id = UUID.randomUUID(),
      reference = reference ?: "MOCK_TXN",
      amount = 1000,
    )

    var mapping = post(urlEqualTo("/transactions"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
          .withStatus(201)
          .withBody(mapper.writeValueAsString(response)),
      )

    if (creditorSubAccountUuid != null) {
      mapping = mapping.withRequestBody(
        matchingJsonPath("$.postings[?(@.type == 'CR' && @.subAccountId == '$creditorSubAccountUuid')]"),
      )
    }
    if (debtorSubAccountUuid != null) {
      mapping = mapping.withRequestBody(
        matchingJsonPath("$.postings[?(@.type == 'DR' && @.subAccountId == '$debtorSubAccountUuid')]"),
      )
    }

    stubFor(mapping)
  }
}
