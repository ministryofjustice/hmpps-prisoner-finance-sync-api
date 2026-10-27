package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.advances

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.AdvancesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.AdvancesApiExtension.Companion.advancesApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncAdvanceRepayResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncAdvanceWriteOffResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRepayRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceWriteOffRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncMigrateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.InMemoryAccountCache
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class, AdvancesApiExtension::class, GeneralLedgerApiExtension::class)
class AdvancesIntegrationTest : IntegrationTestBase() {

  val timeConversionService = TimeConversionService()

  @Autowired
  lateinit var requestCache: InMemoryAccountCache

  @Nested
  inner class PostAdvance {

    @BeforeEach
    fun setup() {
      integrationTestHelpers.clearDB()
      hmppsAuth.stubGrantToken()
      generalLedgerApi.resetAll()
      advancesApi.resetAll()
      requestCache.clear()
    }

    val prisonNumber = "A1234BC"
    val prisonId = "LEI"
    val prisonerAccountId = UUID.randomUUID()
    val prisonerSpendsAccount = UUID.randomUUID()
    val prisonAccountId = UUID.randomUUID()
    val prisonAdvanceAccount = UUID.randomUUID()

    val createdOn = LocalDateTime.now()
    val repaymentStartDate = LocalDateTime.now().plusDays(1)
    val syncCreateAdvanceRecordRequest = SyncCreateAdvanceRecordRequest(
      legacyPaymentProfileId = 1234,
      legacyInformationNumber = "5678",
      prisonNumber = prisonNumber,
      prisonID = prisonId,
      amount = BigDecimal("5.00"),
      createdOn = createdOn,
      repaymentStartDate = repaymentStartDate,
      repaymentAmount = BigDecimal("0.50"),
      reference = "REF",
      createdBy = "USER",
      status = CreateAdvanceRecordRequest.Status.ACTIVE,
      legacyTransactionId = 123,
    )

    @Nested
    inner class CreateAdvance {
      val advanceUuid: UUID = UUID.randomUUID()
      val glUUID: UUID = UUID.randomUUID()

      @BeforeEach
      fun setup() {
        val stubbedAdvanceResponse = AdvanceRecordResponse(
          id = advanceUuid,
          legacyPaymentProfileId = 1234,
          legacyInformationNumber = "5678",
          prisonNumber = "A1234BC",
          prisonID = "LEI",
          amount = 500,
          createdOn = timeConversionService.toUtcInstant(createdOn as LocalDateTime),
          repaymentStartDate = timeConversionService.toUtcInstant(repaymentStartDate as LocalDateTime),
          repaymentAmount = 50,
          reference = "REF",
          createdBy = "USER",
          status = AdvanceRecordResponse.Status.ACTIVE,
          updatedAt = null,
          generalLedgerUUID = glUUID,
        )

        advancesApi.stubPostAdvance(syncCreateAdvanceRecordRequest, stubbedAdvanceResponse)
      }

      private fun postAdvance() = webTestClient.post().uri("/sync/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRecordRequest)
        .exchange()
        .expectStatus()
        .isCreated
        .expectBody<SyncCreateAdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      @Test
      fun `should return a 201 and the created advance`() {
        val expectedSyncCreateAdvanceRecordResponse = SyncCreateAdvanceRecordResponse(
          paymentProfileId = syncCreateAdvanceRecordRequest.legacyPaymentProfileId,
          advanceId = advanceUuid,
        )

        val responseBody = postAdvance()

        assertThat(responseBody.paymentProfileId).isEqualTo(expectedSyncCreateAdvanceRecordResponse.paymentProfileId)
        assertThat(responseBody.advanceId).isEqualTo(expectedSyncCreateAdvanceRecordResponse.advanceId)
      }

      @Test
      fun `should return a 201 when an advance already exists in the mapping table`() {
        integrationTestHelpers.stubGlAccountsAndSubAccountsForPrisonerAndPrison(
          prisonParentAccount = prisonAccountId,
          prisonSubAccount = prisonAdvanceAccount,
          prisonSubAccountRef = "1502:ADV",
          prisonParentAccountRef = syncCreateAdvanceRecordRequest.prisonID,
          prisonerParentAccount = prisonerAccountId,
          prisonerSubAccount = prisonerSpendsAccount,
          prisonerSubAccountRef = "SPENDS",
          prisonerParentAccountRef = syncCreateAdvanceRecordRequest.prisonNumber,
        )

        postAdvance()
        postAdvance()

        advancesApi.verify(1, postRequestedFor(urlPathMatching("/advances")))
      }
    }

    @Test
    fun `should return a 400 when an invalid payload is sent`() {
      webTestClient
        .post()
        .uri("/sync/advances")
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
        .uri("/sync/advances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE__INCORRECT_ROLE")))
        .bodyValue(syncCreateAdvanceRecordRequest)
        .exchange()
        .expectStatus().isForbidden
        .expectBody<ErrorResponse>()
    }

    @Test
    fun `should return a 502 when Advance service returns a 5XX error`() {
      integrationTestHelpers.stubGlAccountsAndSubAccountsForPrisonerAndPrison(
        prisonParentAccount = prisonAccountId,
        prisonSubAccount = prisonAdvanceAccount,
        prisonSubAccountRef = "1502:ADV",
        prisonParentAccountRef = syncCreateAdvanceRecordRequest.prisonID,
        prisonerParentAccount = prisonerAccountId,
        prisonerSubAccount = prisonerSpendsAccount,
        prisonerSubAccountRef = "SPENDS",
        prisonerParentAccountRef = syncCreateAdvanceRecordRequest.prisonNumber,
      )

      advancesApi.stubPostAdvanceReturnsError(500)

      webTestClient
        .post()
        .uri("/sync/advances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRecordRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
        .expectBody<ErrorResponse>()
    }
  }

  @Nested
  inner class WriteOffAdvance {

    val advanceId = UUID.randomUUID()
    val syncCreateAdvanceWriteOffRequest = SyncCreateAdvanceWriteOffRequest(
      writeOffDateTime = LocalDateTime.now(),
      writtenOffBy = "USER",
      writtenOffAmount = BigDecimal("10.12"),
    )

    @BeforeEach
    fun setup() {
      integrationTestHelpers.clearDB()
      hmppsAuth.stubGrantToken()
      generalLedgerApi.resetAll()
      advancesApi.resetAll()
      requestCache.clear()
    }

    @Test
    fun `should return a 201 when a write off is made`() {
      advancesApi.stubPostAdvanceWriteOff(
        syncCreateAdvanceWriteOff = syncCreateAdvanceWriteOffRequest,
        advanceUUID = advanceId,
      )

      val response = webTestClient.post().uri("/sync/advances/{advanceId}/write-off", advanceId)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FINANCE_SYNC__RW")))
        .bodyValue(syncCreateAdvanceWriteOffRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CREATED)
        .expectBody<SyncAdvanceWriteOffResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.advanceId).isEqualTo(advanceId)
    }

    @Test
    fun `should propagate 400 errors from advance service`() {
      advancesApi.stubPostAdvanceWriteOffReturnsStatusCode(
        advanceUUID = advanceId,
        statusCode = 400,
      )

      val response = webTestClient.post().uri("/sync/advances/{advanceId}/write-off", advanceId)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FINANCE_SYNC__RW")))
        .bodyValue(syncCreateAdvanceWriteOffRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.userMessage).isEqualTo("Bad Request from Advances API")
    }

    @Test
    fun `should return a 400 if the advance is not a UUID`() {
      val error = webTestClient.post().uri("/sync/advances/INVALID/write-off")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FINANCE_SYNC__RW")))
        .bodyValue(syncCreateAdvanceWriteOffRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(error.userMessage).contains("Parameter 'advanceId' must be of type UUID")
    }

    @Test
    fun `should return a 400 if the body is invalid`() {
      val error = webTestClient.post().uri("/sync/advances/${UUID.randomUUID()}/write-off")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FINANCE_SYNC__RW")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"hello\": \"world\"}")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(error.userMessage).contains("Invalid request body: ")
    }

    @ParameterizedTest
    @CsvSource(
      "WRONG_ROLE",
      "ROLE_PRISONER_FINANCE_SYNC__RO",
    )
    fun `should return a 403 when when wrong role is provided`(role: String) {
      webTestClient.post().uri("/sync/advances/{advanceId}/write-off", advanceId)
        .headers(setAuthorisation(roles = listOf(role)))
        .bodyValue(syncCreateAdvanceWriteOffRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `should return a 404 when advance service responds with 404`() {
      advancesApi.stubPostAdvanceWriteOffReturnsStatusCode(
        advanceUUID = advanceId,
        statusCode = 404,
      )

      val response = webTestClient.post().uri("/sync/advances/{advanceId}/write-off", advanceId)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FINANCE_SYNC__RW")))
        .bodyValue(syncCreateAdvanceWriteOffRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.userMessage).isEqualTo("Not found")
    }

    @Test
    fun `should return a 502 when advance service responds with 500`() {
      advancesApi.stubPostAdvanceWriteOffReturnsStatusCode(
        advanceUUID = advanceId,
        statusCode = 500,
      )

      val response = webTestClient.post().uri("/sync/advances/{advanceId}/write-off", advanceId)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FINANCE_SYNC__RW")))
        .bodyValue(syncCreateAdvanceWriteOffRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.userMessage).isEqualTo("Bad Gateway - Advances API Unreachable or throwing an error")
    }
  }

  @Nested
  inner class RepayAdvance {

    @BeforeEach
    fun setup() {
      hmppsAuth.stubGrantToken()
      generalLedgerApi.resetAll()
      advancesApi.resetAll()
    }

    @Test
    fun `should return a 201 and the general ledger UUID when the advance repay endpoint is called`() {
      val advanceId = UUID.randomUUID()
      val generalLedgerUUID = UUID.randomUUID()

      val syncCreateAdvanceRepayRequest = SyncCreateAdvanceRepayRequest(
        amount = BigDecimal(10.0),
        transactionId = 123456,
        createdAt = LocalDateTime.now(),
        description = "Deduction-ADV-08052026 D D",
        createdBy = "USER",
        entrySequence = 2,
        caseloadId = "LEI",
      )

      advancesApi.stubPostAdvanceRepayment(
        syncCreateAdvanceRepayRequest = syncCreateAdvanceRepayRequest,
        generalLedgerUUID = generalLedgerUUID,
        advanceUUID = advanceId,
      )

      val advanceRepaymentResponse = webTestClient.post().uri("/sync/advances/$advanceId/repay", advanceId)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRepayRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CREATED)
        .expectBody<SyncAdvanceRepayResponse>()
        .returnResult()
        .responseBody!!

      assertThat(advanceRepaymentResponse.repaymentTransactionId).isEqualTo(generalLedgerUUID)
    }

    @Test
    fun `should return a 400 if advance ID is not UUID`() {
      val syncCreateAdvanceRepayRequest = SyncCreateAdvanceRepayRequest(
        amount = BigDecimal(10.0),
        transactionId = 123456,
        createdAt = LocalDateTime.now(),
        description = "Deduction-ADV-08052026 D D",
        createdBy = "USER",
        entrySequence = 2,
        caseloadId = "LEI",
      )

      webTestClient.post().uri("/sync/advances/NOT_A_UUID/repay")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRepayRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!
    }

    @Test
    fun `should return a 400 if request body is malformed`() {
      webTestClient.post().uri("/sync/advances/NOT_A_UUID/repay")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue("{\"Invalid\" : \"JSON\"}")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!
    }

    @Test
    fun `should return a 404 if advance does not exist`() {
      val advanceId = UUID.randomUUID()

      val syncCreateAdvanceRepayRequest = SyncCreateAdvanceRepayRequest(
        amount = BigDecimal(10.0),
        transactionId = 123456,
        createdAt = LocalDateTime.now(),
        description = "Deduction-ADV-08052026 D D",
        createdBy = "USER",
        entrySequence = 2,
        caseloadId = "LEI",
      )

      advancesApi.stubPostAdvanceReturnsError(404)

      webTestClient.post().uri("/sync/advances/$advanceId/repay")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRepayRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!
    }

    @Test
    fun `should return a 403 if passed role is incorrect`() {
      val advanceId = UUID.randomUUID()

      val syncCreateAdvanceRepayRequest = SyncCreateAdvanceRepayRequest(
        amount = BigDecimal(10.0),
        transactionId = 123456,
        createdAt = LocalDateTime.now(),
        description = "Deduction-ADV-08052026 D D",
        createdBy = "USER",
        entrySequence = 2,
        caseloadId = "LEI",
      )

      webTestClient.post().uri("/sync/advances/$advanceId/repay")
        .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
        .bodyValue(syncCreateAdvanceRepayRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!
    }

    @Test
    fun `should return a 502 if advance service returns a 5XX error`() {
      val advanceId = UUID.randomUUID()

      val syncCreateAdvanceRepayRequest = SyncCreateAdvanceRepayRequest(
        amount = BigDecimal(10.0),
        transactionId = 123456,
        createdAt = LocalDateTime.now(),
        description = "Deduction-ADV-08052026 D D",
        createdBy = "USER",
        entrySequence = 2,
        caseloadId = "LEI",
      )

      advancesApi.stubPostAdvanceReturnsError(500)

      webTestClient.post().uri("/sync/advances/$advanceId/repay")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateAdvanceRepayRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!
    }
  }

  @Nested
  inner class MigrateAdvance {

    @BeforeEach
    fun setup() {
      integrationTestHelpers.clearDB()
      hmppsAuth.stubGrantToken()
      generalLedgerApi.resetAll()
      advancesApi.resetAll()
      requestCache.clear()
    }

    val prisonId = "LEI"
    val prisonNumber = "A1234AA"
    val legacyTransactionId = 12345L
    val legacyPaymentProfileId = 123L
    val legacyInformationNumber = "123-1"

    val syncMigrateAdvanceRecordRequest = SyncMigrateAdvanceRecordRequest(
      legacyPaymentProfileId = legacyPaymentProfileId,
      legacyInformationNumber = legacyInformationNumber,
      prisonNumber = prisonNumber,
      prisonID = prisonId,
      createdOn = LocalDateTime.now(),
      createdBy = "USER",
      repaymentStartDate = LocalDateTime.now(),
      repaymentAmount = BigDecimal(10.0),
      reference = "",
      comment = "You owe me money",
      status = CreateAdvanceRecordRequest.Status.ACTIVE,
      legacyTransactionId = legacyTransactionId,
      amountOutstanding = BigDecimal(10.0),
      amountIssued = BigDecimal(12.0),
      balanceCalculationTime = LocalDateTime.now(),
    )

    val expectedAdvanceMigrationRequest = AdvanceMigrationRequest(
      legacyPaymentProfileId = legacyPaymentProfileId,
      legacyInformationNumber = legacyInformationNumber,
      prisonNumber = prisonNumber,
      prisonID = prisonId,
      amountIssued = syncMigrateAdvanceRecordRequest.amountIssued.toPence(),
      amountOutstanding = syncMigrateAdvanceRecordRequest.amountOutstanding.toPence(),
      createdOn = timeConversionService.toUtcInstant(syncMigrateAdvanceRecordRequest.createdOn),
      migrationTimestamp = timeConversionService.toUtcInstant(syncMigrateAdvanceRecordRequest.balanceCalculationTime),
      repaymentStartDate = timeConversionService.toUtcInstant(syncMigrateAdvanceRecordRequest.repaymentStartDate),
      repaymentAmount = syncMigrateAdvanceRecordRequest.repaymentAmount.toPence(),
      createdBy = syncMigrateAdvanceRecordRequest.createdBy,
      status = AdvanceMigrationRequest.Status.valueOf(syncMigrateAdvanceRecordRequest.status.name),
      reference = syncMigrateAdvanceRecordRequest.reference,
      comment = syncMigrateAdvanceRecordRequest.comment,
      legacyTransactionId = legacyTransactionId,
    )

    @BeforeEach
    fun setupMocks() {
      generalLedgerApi.resetAll()
      val prisonAccountId = UUID.randomUUID()
      val prisonAdvanceAccountRef = "1502:ADV"
      // Stub prison account and sub account
      generalLedgerApi.stubGetAccount(
        reference = prisonId,
        returnUuid = prisonAccountId,
        subAccounts = listOf(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = prisonAdvanceAccountRef,
            parentAccountId = prisonAccountId,
            createdBy = "TEST USER",
            createdAt = Instant.now(),
          ),
        ),
        scenarioName = "Prison Account",
      )

      val prisonerAccountId = UUID.randomUUID()
      val prisonerAdvanceAccountRef = "SPENDS"
      // Stub prisoner account and sub account
      generalLedgerApi.stubGetAccount(
        reference = prisonNumber,
        returnUuid = prisonerAccountId,
        subAccounts = listOf(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = prisonerAdvanceAccountRef,
            parentAccountId = prisonerAccountId,
            createdBy = "TEST USER",
            createdAt = Instant.now(),
          ),
        ),
        scenarioName = "Prisoner Account",
      )
    }

    @Test
    fun `should return a 201 when the advance migrate endpoint is called`() {
      val createdOn = LocalDateTime.now()
      val repaymentStartDate = LocalDateTime.now().plusDays(1)
      val advanceUuid: UUID = UUID.randomUUID()
      val glUUID: UUID = UUID.randomUUID()

      val advanceRecordResponse = AdvanceRecordResponse(
        id = advanceUuid,
        legacyPaymentProfileId = legacyPaymentProfileId,
        legacyInformationNumber = legacyInformationNumber,
        prisonNumber = prisonNumber,
        prisonID = prisonId,
        amount = syncMigrateAdvanceRecordRequest.amountIssued.toPence(),
        createdOn = timeConversionService.toUtcInstant(createdOn as LocalDateTime),
        repaymentStartDate = timeConversionService.toUtcInstant(repaymentStartDate as LocalDateTime),
        repaymentAmount = syncMigrateAdvanceRecordRequest.repaymentAmount.toPence(),
        reference = syncMigrateAdvanceRecordRequest.reference,
        createdBy = syncMigrateAdvanceRecordRequest.createdBy,
        status = AdvanceRecordResponse.Status.ACTIVE,
        updatedAt = null,
        generalLedgerUUID = null,
      )

      advancesApi.stubPostMigrateAdvance(expectedAdvanceMigrationRequest, advanceRecordResponse)

      val response = webTestClient.post().uri("/migrate/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncMigrateAdvanceRecordRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CREATED)
        .expectBody<SyncCreateAdvanceRecordResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.paymentProfileId).isEqualTo(legacyPaymentProfileId)
      assertThat(response.advanceId).isEqualTo(advanceUuid)
    }

    @Test
    fun `should return 400 bad request when called with an invalid body`() {
      webTestClient.post().uri("/migrate/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue("{\"Invalid\" : \"JSON\"}")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!
    }

    @Test
    fun `should propagate error 400 when advances returns 400`() {
      advancesApi.stubPostMigrateAdvanceReturnsStatusCode(400)

      val response = webTestClient.post().uri("/migrate/advances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncMigrateAdvanceRecordRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.userMessage).isEqualTo("Bad Request from Advances API")
    }

    @Test
    fun `should return 403 forbidden when called with an invalid roles`() {
      webTestClient.post().uri("/migrate/advances")
        .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
        .bodyValue(syncMigrateAdvanceRecordRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!
    }
  }
}
