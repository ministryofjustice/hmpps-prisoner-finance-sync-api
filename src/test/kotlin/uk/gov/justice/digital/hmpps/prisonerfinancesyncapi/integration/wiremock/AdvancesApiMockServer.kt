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
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AccountAdvanceBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRepayRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceWriteOffRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.time.Instant
import java.util.UUID

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
  private val timeConversionService = TimeConversionService()

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
    stubFor(
      post("/advances")
        .withRequestBody(
          matchingJsonPath(
            "$.legacyPaymentProfileId",
            equalTo(advanceRequest.legacyPaymentProfileId.toString()),
          ),
        )
        .withRequestBody(matchingJsonPath("$.legacyInformationNumber", equalTo(advanceRequest.legacyInformationNumber)))
        .withRequestBody(matchingJsonPath("$.prisonNumber", equalTo(advanceRequest.prisonNumber)))
        .withRequestBody(matchingJsonPath("$.prisonID", equalTo(advanceRequest.prisonID)))
        .withRequestBody(matchingJsonPath("$.amount", equalTo(advanceRequest.amount.toPence().toString())))
        .withRequestBody(
          matchingJsonPath(
            "$.createdOn",
            equalTo(timeConversionService.toUtcInstant(advanceRequest.createdOn).toString()),
          ),
        )
        .withRequestBody(
          matchingJsonPath(
            "$.repaymentStartDate",
            equalTo(timeConversionService.toUtcInstant(advanceRequest.repaymentStartDate).toString()),
          ),
        )
        .withRequestBody(
          matchingJsonPath(
            "$.repaymentAmount",
            equalTo(advanceRequest.repaymentAmount.toPence().toString()),
          ),
        )
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

  fun stubPostAdvanceReturnsError(errorCode: Int) {
    stubFor(
      post(urlPathMatching("^/advances.*"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(errorCode)
            .withBody(
              """
              {
                  "status": $errorCode,
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

  fun stubPostAdvanceRepayment(syncCreateAdvanceRepayRequest: SyncCreateAdvanceRepayRequest, generalLedgerUUID: UUID, advanceUUID: UUID) {
    val advanceRecordResponse = AdvanceRepaymentResponse(
      id = UUID.randomUUID(),
      advanceId = advanceUUID,
      amount = syncCreateAdvanceRepayRequest.amount.toPence(),
      createdAt = timeConversionService.toUtcInstant(syncCreateAdvanceRepayRequest.createdAt),
      createdBy = syncCreateAdvanceRepayRequest.createdBy,
      transactionId = generalLedgerUUID,
      description = syncCreateAdvanceRepayRequest.description,
      legacyTransactionId = syncCreateAdvanceRepayRequest.transactionId,
      entrySequence = syncCreateAdvanceRepayRequest.entrySequence,
    )

    stubFor(
      post("/advances/$advanceUUID/repay")
        .withRequestBody(matchingJsonPath("$.amount", equalTo(syncCreateAdvanceRepayRequest.amount.toPence().toString())))
        .withRequestBody(matchingJsonPath("$.createdAt", equalTo(timeConversionService.toUtcInstant(syncCreateAdvanceRepayRequest.createdAt).toString())))
        .withRequestBody(matchingJsonPath("$.createdBy", equalTo(syncCreateAdvanceRepayRequest.createdBy)))
        .withRequestBody(matchingJsonPath("$.description", equalTo(syncCreateAdvanceRepayRequest.description)))
        .withRequestBody(matchingJsonPath("$.entrySequence", equalTo(syncCreateAdvanceRepayRequest.entrySequence.toString())))
        .withRequestBody(matchingJsonPath("$.legacyTransactionId", equalTo(syncCreateAdvanceRepayRequest.transactionId.toString())))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(201)
            .withBody(mapper.writeValueAsString(advanceRecordResponse)),
        ),
    )
  }

  fun stubPostAdvanceWriteOff(syncCreateAdvanceWriteOff: SyncCreateAdvanceWriteOffRequest, advanceUUID: UUID) {
    val advanceRecordResponse = AdvanceRecordResponse(
      id = advanceUUID,
      legacyPaymentProfileId = 123,
      legacyInformationNumber = "1233",
      prisonNumber = "A123XS",
      prisonID = "LWI",
      amount = 1,
      createdOn = Instant.now(),
      repaymentStartDate = Instant.now(),
      repaymentAmount = 123,
      createdBy = "TEST",
      status = AdvanceRecordResponse.Status.WRITTEN_OFF,
      reference = "",
      comment = "",
      updatedAt = null,
      generalLedgerUUID = UUID.randomUUID(),
    )

    stubFor(
      post("/advances/$advanceUUID/write-off")
        .withRequestBody(matchingJsonPath("$.writtenOffBy", equalTo(syncCreateAdvanceWriteOff.writtenOffBy)))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(201)
            .withBody(mapper.writeValueAsString(advanceRecordResponse)),
        ),
    )
  }

  fun stubPostAdvanceWriteOffReturnsStatusCode(advanceUUID: UUID, statusCode: Int) {
    stubFor(
      post("/advances/$advanceUUID/write-off")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(statusCode)
            .withBody(
              """
               {
                  "status": $statusCode,
                  "errorCode": null,
                  "userMessage": "Server Error",
                  "developerMessage": "Server Error",
                  "moreInfo": null
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubPostMigrateAdvance(advanceMigrationRequest: AdvanceMigrationRequest, advanceRecordResponse: AdvanceRecordResponse) {
    stubFor(
      post("/migrate/advances")
        .withRequestBody(
          matchingJsonPath(
            "$.legacyPaymentProfileId",
            equalTo(advanceMigrationRequest.legacyPaymentProfileId.toString()),
          ),
        )
        .withRequestBody(matchingJsonPath("$.legacyInformationNumber", equalTo(advanceMigrationRequest.legacyInformationNumber)))
        .withRequestBody(matchingJsonPath("$.prisonNumber", equalTo(advanceMigrationRequest.prisonNumber)))
        .withRequestBody(matchingJsonPath("$.prisonID", equalTo(advanceMigrationRequest.prisonID)))
        .withRequestBody(matchingJsonPath("$.amountIssued", equalTo(advanceMigrationRequest.amountIssued.toString())))
        .withRequestBody(matchingJsonPath("$.amountOutstanding", equalTo(advanceMigrationRequest.amountOutstanding.toString())))
        .withRequestBody(
          matchingJsonPath(
            "$.createdOn",
            equalTo(advanceMigrationRequest.createdOn.toString()),
          ),
        )
        .withRequestBody(matchingJsonPath("$.migrationTimestamp", equalTo(advanceMigrationRequest.migrationTimestamp.toString())))
        .withRequestBody(
          matchingJsonPath(
            "$.repaymentStartDate",
            equalTo(advanceMigrationRequest.repaymentStartDate.toString()),
          ),
        )
        .withRequestBody(
          matchingJsonPath(
            "$.repaymentAmount",
            equalTo(advanceMigrationRequest.repaymentAmount.toString()),
          ),
        )
        .withRequestBody(matchingJsonPath("$.reference", equalTo(advanceMigrationRequest.reference)))
        .withRequestBody(matchingJsonPath("$.createdBy", equalTo(advanceMigrationRequest.createdBy)))
        .withRequestBody(matchingJsonPath("$.status", equalTo(advanceMigrationRequest.status.toString())))
        .withRequestBody(matchingJsonPath("$.legacyTransactionId", equalTo(advanceMigrationRequest.legacyTransactionId.toString())))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(201)
            .withBody(mapper.writeValueAsString(advanceRecordResponse)),
        ),
    )
  }

  fun stubPostMigrateAdvanceReturnsStatusCode(statusCode: Int) {
    stubFor(
      post("/migrate/advances")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(statusCode)
            .withBody(
              """
               {
                  "status": $statusCode,
                  "errorCode": null,
                  "userMessage": "Server Error",
                  "developerMessage": "Server Error",
                  "moreInfo": null
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubGetAdvanceBalanceForAccount(
    prisonNumber: String,
    advanceBalanceResponse: AccountAdvanceBalanceResponse = AccountAdvanceBalanceResponse(
      advancesAmount = 0,
      balancesOutstanding = 0,
      weeklyPaymentAmount = 0,
      advanceCount = 0,
      balanceDateTime = Instant.now(),
    ),
  ) {
    stubFor(
      get(urlPathEqualTo("/advances/account/$prisonNumber/balance"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(200)
            .withBody(mapper.writeValueAsString(advanceBalanceResponse)),
        ),
    )
  }
  fun stubGetAdvanceBalanceForAccountReturnsError(
    prisonNumber: String,
    statusCode: Int,
  ) {
    stubFor(
      get(urlPathEqualTo("/advances/account/$prisonNumber/balance"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .withStatus(statusCode)
            .withBody(
              """
               {
                  "status": $statusCode,
                  "errorCode": null,
                  "userMessage": "Server Error",
                  "developerMessage": "Server Error",
                  "moreInfo": null
              }
              """.trimIndent(),
            ),
        ),
    )
  }
}
