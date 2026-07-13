package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import com.github.tomakehurst.wiremock.verification.FindRequestsResult
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PagedResponseSearchTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SearchTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.TransactionResponse
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.Instant
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

  // POST /sub-accounts/{subAccountId}/balance
  fun stubPostSubAccountBalance(subAccountID: UUID, amount: Long, balanceDateTime: Instant) {
    stubFor(
      post(urlPathEqualTo("/sub-accounts/$subAccountID/balance"))
        .withRequestBody(matchingJsonPath("$.amount", equalTo(amount.toString())))
        .withRequestBody(matchingJsonPath("$.balanceDateTime", equalTo(balanceDateTime.toString())))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(201)
            .withBody(
              """
                        {
                          "amount": $amount,
                          "subAccountId": "$subAccountID",
                          "balanceDateTime": "$balanceDateTime"
                        }
              """.trimIndent(),
            ),
        ),
    )
  }

  // POST /sub-accounts/{subAccountId}/balance
  fun stubPostSubAccountBalanceNotFound(subAccountID: UUID, amount: Long, balanceDateTime: Instant) {
    stubFor(
      post(urlPathEqualTo("/sub-accounts/$subAccountID/balance"))
        .withRequestBody(matchingJsonPath("$.amount", equalTo(amount.toString())))
        .withRequestBody(matchingJsonPath("$.balanceDateTime", equalTo(balanceDateTime.toString())))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(404)
            .withBody(
              """
                      {
                      "status": 404,
                      "errorCode": "NotFound",
                      "userMessage": "Not found Error",
                      "developerMessage": "Test not found error",
                      "moreInfo": "more info"
                    }
              """.trimIndent(),
            ),
        ),
    )
  }

  // GET /accounts?reference={ref} -> Returns List<AccountResponse>
  fun stubGetAccount(
    reference: String,
    returnUuid: UUID = UUID.randomUUID(),
    subAccounts: List<SubAccountResponse> = emptyList(),
    scenarioName: String? = null,
    scenarioState: String = STARTED,
    nextState: String = "SECOND_CALL",
  ) {
    val type = if (reference.length > 3) AccountResponse.Type.PRISONER else AccountResponse.Type.PRISON
    val response = AccountResponse(
      id = returnUuid,
      reference = reference,
      createdAt = Instant.now(),
      createdBy = "MOCK_USER",
      subAccounts = subAccounts,
      type = type,
    )

    stubFor(
      get(urlPathEqualTo("/accounts"))
        .apply {
          if (scenarioName != null) {
            inScenario(scenarioName)
              .whenScenarioStateIs(scenarioState)
              .willSetStateTo(nextState)
          }
        }
        .withQueryParam("reference", equalTo(reference))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(mapper.writeValueAsString(listOf(response))),
        ),
    )
  }

  fun stubGetAccountNotFound(
    reference: String,
    scenarioName: String? = null,
    scenarioState: String = STARTED,
    nextState: String = "SECOND_CALL",
  ) {
    stubFor(
      get(urlPathEqualTo("/accounts"))
        .apply {
          if (scenarioName != null) {
            inScenario(scenarioName)
              .whenScenarioStateIs(scenarioState)
              .willSetStateTo(nextState)
          }
        }
        .withQueryParam("reference", equalTo(reference))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody("[]"),
        ),
    )
  }

  // POST /accounts -> Returns 500
  fun stubCreateAccountReturnsServerError(reference: String) {
    stubFor(
      post(urlEqualTo("/accounts"))
        .withRequestBody(matchingJsonPath("$.accountReference", equalTo(reference)))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(500)
            .withBody(
              """
              {
                  "status": 500,
                  "errorCode": null,
                  "userMessage": "GL Server Error",
                  "developerMessage": "GL Server Error",
                  "moreInfo": null
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  // POST /accounts -> Returns Conflict 409
  fun stubCreateAccountReturnsConflict(reference: String) {
    stubFor(
      post(urlEqualTo("/accounts"))
        .withRequestBody(matchingJsonPath("$.accountReference", equalTo(reference)))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(409)
            .withBody(
              """
              {
                  "status": 409,
                  "errorCode": null,
                  "userMessage": "Duplicate account reference: $reference",
                  "developerMessage": "Duplicate account reference: $reference",
                  "moreInfo": null
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  // POST /accounts/{uuid}/sub-accounts -> Returns Conflict 409
  fun stubCreateSubAccountReturnsConflict(parentId: UUID, reference: String) {
    stubFor(
      post(urlEqualTo("/accounts/$parentId/sub-accounts"))
        .withRequestBody(matchingJsonPath("$.subAccountReference", equalTo(reference)))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(409)
            .withBody(
              """
              {
                  "status": 409,
                  "errorCode": null,
                  "userMessage": "Duplicate sub account reference: $reference parentId: $parentId",
                  "developerMessage": "Duplicate account reference: $reference  parentId: $parentId",
                  "moreInfo": null
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  // POST /accounts/{uuid}/sub-accounts -> 500
  fun stubCreateSubAccountReturnsServerError(parentId: UUID, reference: String) {
    stubFor(
      post(urlEqualTo("/accounts/$parentId/sub-accounts"))
        .withRequestBody(matchingJsonPath("$.subAccountReference", equalTo(reference)))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(500)
            .withBody(
              """
              {
                  "status": 500,
                  "errorCode": null,
                  "userMessage": "GL Server Error",
                  "developerMessage": "GL Server Error",
                  "moreInfo": null
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  // POST /accounts -> Returns Single AccountResponse
  fun stubCreateAccount(reference: String, returnUuid: UUID = UUID.randomUUID()) {
    val type = if (reference.length > 3) AccountResponse.Type.PRISONER else AccountResponse.Type.PRISON

    val response = AccountResponse(
      id = returnUuid,
      reference = reference,
      createdAt = Instant.now(),
      createdBy = "MOCK_USER",
      subAccounts = emptyList(),
      type = type,
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

  // GET /sub-accounts
  fun stubGetSubAccount(
    parentReference: String,
    subAccountReference: String,
    parentAccountId: UUID = UUID.randomUUID(),
    response: List<SubAccountResponse>? = null,
  ) {
    val subAccount = SubAccountResponse(
      id = UUID.randomUUID(),
      parentAccountId = parentAccountId,
      reference = subAccountReference,
      createdAt = Instant.now(),
      createdBy = "MOCK_USER",
    )

    stubFor(
      get(urlPathEqualTo("/sub-accounts"))
        .withQueryParam("reference", equalTo(subAccountReference))
        .withQueryParam("accountReference", equalTo(parentReference))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(
              mapper.writeValueAsString(
                response ?: listOf(subAccount),
              ),
            ),
        ),
    )
  }

  // GET /sub-accounts
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
  fun stubCreateSubAccount(parentId: UUID, reference: String, returnUuid: String = UUID.randomUUID().toString()) {
    val response = SubAccountResponse(
      id = UUID.fromString(returnUuid),
      parentAccountId = parentId,
      reference = reference,
      createdAt = Instant.now(),
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

  fun verifyTransactionPosted(
    times: Int = 1,
    debtorSubAccountUuid: String? = null,
    creditorSubAccountUuid: String? = null,
  ): FindRequestsResult {
    var verification = postRequestedFor(urlPathEqualTo("/transactions"))
      .withHeader("Idempotency-Key", matching(".*"))

    if (debtorSubAccountUuid != null) {
      verification = verification.withRequestBody(
        matchingJsonPath("$.postings[?(@.type == 'DR' && @.subAccountId == '$debtorSubAccountUuid')]"),
      )
    }

    if (creditorSubAccountUuid != null) {
      verification = verification.withRequestBody(
        matchingJsonPath("$.postings[?(@.type == 'CR' && @.subAccountId == '$creditorSubAccountUuid')]"),
      )
    }

    verify(times, verification)

    return generalLedgerApi.findRequestsMatching(verification.build())
  }

  // POST /transactions
  fun stubPostTransaction(
    creditorSubAccountUuid: String? = null,
    debtorSubAccountUuid: String? = null,
    reference: String? = null,
    returnUUID: UUID = UUID.randomUUID(),
    postings: List<PostingResponse> = emptyList(),
    amount: Long = 1000,
  ): TransactionResponse {
    val response = TransactionResponse(
      id = returnUUID,
      reference = reference ?: "MOCK_TXN",
      amount = amount,
      createdBy = "MOCK_USER",
      createdAt = Instant.now(),
      description = "Mock Transaction Description",
      timestamp = Instant.now(),
      postings = postings,
    )

    var mapping = post(urlEqualTo("/transactions"))
      .withHeader("Idempotency-Key", matching(".*"))
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

    return response
  }

  // POST /transactions
  fun stubPostTransactionReturnsBadRequest() {
    var mapping = post(urlEqualTo("/transactions"))
      .withHeader("Idempotency-Key", matching(".*"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
          .withStatus(400)
          .withBody(
            """
                {
                  "status": 400,
                  "errorCode": "BadRequest",
                  "userMessage": "Bad Request",
                  "developerMessage": "Bad Request",
                  "moreInfo": "more info"
                }
              """,
          ),
      )

    stubFor(mapping)
  }

  // GET /sub-accounts/$accountId/balance
  fun stubGetSubAccountBalance(accountId: UUID, amount: Long): SubAccountBalanceResponse {
    val response = SubAccountBalanceResponse(
      subAccountId = accountId,
      balanceDateTime = Instant.now(),
      amount = amount,
    )

    stubFor(
      get(urlPathEqualTo("/sub-accounts/$accountId/balance"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(mapper.writeValueAsString(response)),
        ),
    )
    return response
  }

  // GET /transactions/transactionUUID

  fun stubGetTransactionByUUID(
    transactionUUID: UUID,
    reference: String,
    createdAt: Instant,
    timeStamp: Instant,
    amount: Long,
    postings: List<PostingResponse>,
  ): TransactionResponse {
    val response = TransactionResponse(
      id = transactionUUID,
      reference = reference,
      createdBy = "TEST",
      createdAt = createdAt,
      description = "",
      timestamp = timeStamp,
      amount = amount,
      postings = postings,
    )

    stubFor(
      get(urlPathEqualTo("/transactions/$transactionUUID"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(mapper.writeValueAsString(response)),
        ),
    )

    return response
  }

  fun stubGetTransactionByUUIDNotFound(
    transactionUUID: UUID,
  ): ErrorResponse {
    val response = ErrorResponse(status = 404)
    stubFor(
      get(urlPathEqualTo("/transactions/$transactionUUID"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(404)
            .withBody(mapper.writeValueAsString(response)),
        ),
    )
    return response
  }

  fun stubSearchTransactionThrowsOutOfBoundsException() {
    stubFor(
      post(urlPathEqualTo("/transactions/search"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(400)
            .withBody(
              """
                {
                  "status": 400,
                  "errorCode": "BadRequest",
                  "userMessage": "Page requested is out of range",
                  "developerMessage": "Page requested is out of range",
                  "moreInfo": "more info"
                }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubSearchTransactionsByUUIDs(
    glUUIDs: List<UUID>,
    transactionResponses: List<SearchTransactionResponse>,
  ): PagedResponseSearchTransactionResponse {
    // This ensures that the mock behaves in the same way as the GL
    val results = transactionResponses.filter { glUUIDs.contains(it.id) }

    val pagedResponse = PagedResponseSearchTransactionResponse(
      content = results,
      pageNumber = 1,
      pageSize = results.size,
      totalElements = results.size.toLong(),
      totalPages = 1,
      isLastPage = true,
    )

    stubFor(
      post(urlPathEqualTo("/transactions/search"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(mapper.writeValueAsString(pagedResponse)),
        ),
    )
    return pagedResponse
  }

  fun stubSearchTransactionsByUUIDsThrows500() {
    stubFor(
      post(urlPathEqualTo("/transactions/search"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(500)
            .withBody(
              """
                {
                  "status": 500,
                  "errorCode": "ServerError",
                  "userMessage": "General Ledger Server Error",
                  "developerMessage": "General Ledger Server Error",
                  "moreInfo": "more info"
                }
              """.trimIndent(),
            ),
        ),
    )
  }
}
