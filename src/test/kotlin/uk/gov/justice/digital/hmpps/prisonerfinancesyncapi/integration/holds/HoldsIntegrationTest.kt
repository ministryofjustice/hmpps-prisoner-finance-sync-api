package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.holds

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HoldsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HoldsApiExtension.Companion.holdsApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.HoldsMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.HoldsMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncReleaseHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncReleasedHoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.InMemoryAccountCache
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.LedgerAccountMappingService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPounds
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class, HoldsApiExtension::class)
class HoldsIntegrationTest(@Autowired private val holdsMappingRepository: HoldsMappingRepository) : IntegrationTestBase() {

  private val accountMappingService = LedgerAccountMappingService()

  private val wiremockClient = WireMock(8092)
  val timeConversionService = TimeConversionService()
  val mapper = ObjectMapper()

  @Autowired
  lateinit var requestCache: InMemoryAccountCache

  @BeforeEach
  fun setup() {
    integrationTestHelpers.clearDB()
    generalLedgerApi.resetAll()
    holdsApi.resetAll()
    hmppsAuth.stubGrantToken()
    requestCache.clear()
  }

  val prisonSubaccountUUID = UUID.randomUUID()
  val prisonerSubaccountUUID = UUID.randomUUID()

  @Nested
  @DisplayName("postHolds")
  inner class PostHolds {
    val prisonParentAccount = UUID.randomUUID()
    val prisonerParentAccount = UUID.randomUUID()

    @Test
    fun `should return a 201 when a hold is created and create GL subAccounts if they do not exist`() {
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
        holdTransactionId = 12345,
      )

      val expectedHoldRequest = CreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountRef = CreateHoldRequest.SubAccountRef.CASH,
        legacyHoldNumber = 123456789,
        createdAt = timeConversionService.toUtcInstant(syncHoldRequest.createdAt),
        createdBy = "USER",
        holdFromDate = timeConversionService.toUtcInstant(syncHoldRequest.holdFromDate),
        holdUntilDate = timeConversionService.toUtcInstant(syncHoldRequest.holdUntilDate as LocalDateTime),
        isReleased = false,
        description = "Test Hold",
        holdType = CreateHoldRequest.HoldType.WHF,
        holdLocation = "LEI",
        amount = syncHoldRequest.amount.toPence(),
      )

      integrationTestHelpers.stubCreateGlSubAccountsForPrisonerAndPrison(
        prisonParentAccount = prisonParentAccount,
        prisonSubAccount = prisonSubaccountUUID,
        prisonSubAccountRef = "2199:${syncHoldRequest.holdType}",
        prisonParentAccountRef = syncHoldRequest.holdLocation,
        prisonerParentAccount = prisonerParentAccount,
        prisonerSubAccount = prisonerSubaccountUUID,
        prisonerSubAccountRef = accountMappingService.mapPrisonerSubAccount(syncHoldRequest.subAccountCode),
        prisonerParentAccountRef = syncHoldRequest.prisonNumber,
      )

      holdsApi.stubPostHold(expectedHoldRequest)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncHoldRequest.holdLocation}")))
      generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncHoldRequest.prisonNumber}")))

      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts")))

      generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonParentAccount/sub-accounts")))
      generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonerParentAccount/sub-accounts")))
    }

    @Test
    fun `should return a 201 when a hold is created and create GL subAccounts and parent accounts if they do not exist`() {
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
        holdTransactionId = 12345,
      )

      val expectedHoldRequest = CreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountRef = CreateHoldRequest.SubAccountRef.CASH,
        legacyHoldNumber = 123456789,
        createdAt = timeConversionService.toUtcInstant(syncHoldRequest.createdAt),
        createdBy = "USER",
        holdFromDate = timeConversionService.toUtcInstant(syncHoldRequest.holdFromDate),
        holdUntilDate = timeConversionService.toUtcInstant(syncHoldRequest.holdUntilDate as LocalDateTime),
        isReleased = false,
        description = "Test Hold",
        holdType = CreateHoldRequest.HoldType.WHF,
        holdLocation = "LEI",
        amount = syncHoldRequest.amount.toPence(),
      )

      integrationTestHelpers.stubCreateGlParentAccountAndSubAccountForPrisonerAndPrison(
        prisonParentAccount = prisonParentAccount,
        prisonSubAccount = prisonSubaccountUUID,
        prisonSubAccountRef = "2199:${syncHoldRequest.holdType}",
        prisonParentAccountRef = syncHoldRequest.holdLocation,
        prisonerParentAccount = prisonerParentAccount,
        prisonerSubAccount = prisonerSubaccountUUID,
        prisonerSubAccountRef = accountMappingService.mapPrisonerSubAccount(syncHoldRequest.subAccountCode),
        prisonerParentAccountRef = syncHoldRequest.prisonNumber,
      )

      holdsApi.stubPostHold(expectedHoldRequest)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncHoldRequest.holdLocation}")))
      generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncHoldRequest.prisonNumber}")))

      generalLedgerApi.verify(2, postRequestedFor(urlPathMatching("/accounts")))

      generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonParentAccount/sub-accounts")))
      generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonerParentAccount/sub-accounts")))
    }

    @Test
    fun `should return a 201 when a hold is created`() {
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
        holdTransactionId = 12345,
      )

      val expectedHoldRequest = CreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountRef = CreateHoldRequest.SubAccountRef.CASH,
        legacyHoldNumber = 123456789,
        createdAt = timeConversionService.toUtcInstant(syncHoldRequest.createdAt),
        createdBy = "USER",
        holdFromDate = timeConversionService.toUtcInstant(syncHoldRequest.holdFromDate),
        holdUntilDate = timeConversionService.toUtcInstant(syncHoldRequest.holdUntilDate as LocalDateTime),
        isReleased = false,
        description = "Test Hold",
        holdType = CreateHoldRequest.HoldType.WHF,
        holdLocation = "LEI",
        amount = syncHoldRequest.amount.toPence(),
      )

      integrationTestHelpers.stubGlAccountsAndSubAccountsForPrisonerAndPrison(
        prisonParentAccount = prisonParentAccount,
        prisonSubAccount = prisonSubaccountUUID,
        prisonSubAccountRef = "2199:${syncHoldRequest.holdType}",
        prisonParentAccountRef = syncHoldRequest.holdLocation,
        prisonerParentAccount = prisonerParentAccount,
        prisonerSubAccount = prisonerSubaccountUUID,
        prisonerSubAccountRef = accountMappingService.mapPrisonerSubAccount(syncHoldRequest.subAccountCode),
        prisonerParentAccountRef = syncHoldRequest.prisonNumber,
      )

      holdsApi.stubPostHold(expectedHoldRequest)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isCreated

      generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncHoldRequest.holdLocation}")))
      generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${syncHoldRequest.prisonNumber}")))

      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/accounts")))
      generalLedgerApi.verify(0, postRequestedFor(urlPathMatching("/sub-accounts.*")))
    }

    @Test
    fun `should return a 201 when a hold already exists in the mapping table`() {
      val legacyHoldNumber = 123456789L
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23111",
        subAccountCode = 2101,
        holdNumber = legacyHoldNumber,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "HOA",
        holdLocation = "LEI",
        amount = BigDecimal("20"),
        holdTransactionId = 12345,
      )

      val expectedHoldRequest = CreateHoldRequest(
        prisonNumber = syncHoldRequest.prisonNumber,
        subAccountRef = CreateHoldRequest.SubAccountRef.CASH,
        legacyHoldNumber = syncHoldRequest.holdNumber,
        createdAt = timeConversionService.toUtcInstant(syncHoldRequest.createdAt),
        createdBy = syncHoldRequest.createdBy,
        holdFromDate = timeConversionService.toUtcInstant(syncHoldRequest.holdFromDate),
        holdUntilDate = timeConversionService.toUtcInstant(syncHoldRequest.holdUntilDate as LocalDateTime),
        isReleased = syncHoldRequest.isReleased,
        description = syncHoldRequest.description,
        holdType = CreateHoldRequest.HoldType.HOA,
        holdLocation = syncHoldRequest.holdLocation,
        amount = syncHoldRequest.amount.toPence(),
      )

      integrationTestHelpers.stubGlAccountsAndSubAccountsForPrisonerAndPrison(
        prisonParentAccount = prisonParentAccount,
        prisonSubAccount = prisonSubaccountUUID,
        prisonSubAccountRef = "2199:${syncHoldRequest.holdType}",
        prisonParentAccountRef = syncHoldRequest.holdLocation,
        prisonerParentAccount = prisonerParentAccount,
        prisonerSubAccount = prisonerSubaccountUUID,
        prisonerSubAccountRef = accountMappingService.mapPrisonerSubAccount(syncHoldRequest.subAccountCode),
        prisonerParentAccountRef = syncHoldRequest.prisonNumber,
      )
      holdsApi.stubPostHold(expectedHoldRequest)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isEqualTo(201)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isEqualTo(201)

      wiremockClient.verifyThat(1, postRequestedFor(urlPathMatching("/holds")))
    }

    @Test
    fun `should return a 502 when the hold service returns an error 500`() {
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
        holdTransactionId = 12345,
      )

      val expectedHoldRequest = CreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountRef = CreateHoldRequest.SubAccountRef.CASH,
        legacyHoldNumber = 123456789,
        createdAt = timeConversionService.toUtcInstant(syncHoldRequest.createdAt),
        createdBy = "USER",
        holdFromDate = timeConversionService.toUtcInstant(syncHoldRequest.holdFromDate),
        holdUntilDate = timeConversionService.toUtcInstant(syncHoldRequest.holdUntilDate as LocalDateTime),
        isReleased = false,
        description = "Test Hold",
        holdType = CreateHoldRequest.HoldType.WHF,
        holdLocation = "LEI",
        amount = syncHoldRequest.amount.toPence(),
      )

      integrationTestHelpers.stubGlAccountsAndSubAccountsForPrisonerAndPrison(
        prisonParentAccount = prisonParentAccount,
        prisonSubAccount = prisonSubaccountUUID,
        prisonSubAccountRef = "2199:${syncHoldRequest.holdType}",
        prisonParentAccountRef = syncHoldRequest.holdLocation,
        prisonerParentAccount = prisonerParentAccount,
        prisonerSubAccount = prisonerSubaccountUUID,
        prisonerSubAccountRef = accountMappingService.mapPrisonerSubAccount(syncHoldRequest.subAccountCode),
        prisonerParentAccountRef = syncHoldRequest.prisonNumber,
      )

      holdsApi.stubPostHoldReturnsError(expectedHoldRequest)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isEqualTo(502)
    }

    @Test
    fun `should return a 502 when the General Ledger service returns an error 500`() {
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
        holdTransactionId = 12345,
      )

      generalLedgerApi.stubGetAccountReturnsError(syncHoldRequest.holdLocation)
      generalLedgerApi.stubGetAccountReturnsError(syncHoldRequest.prisonNumber)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isEqualTo(502)
    }

    @Test
    fun `should return a 400 when the hold service returns an error 400`() {
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
        holdTransactionId = 12345,
      )

      val expectedHoldRequest = CreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountRef = CreateHoldRequest.SubAccountRef.CASH,
        legacyHoldNumber = 123456789,
        createdAt = timeConversionService.toUtcInstant(syncHoldRequest.createdAt),
        createdBy = "USER",
        holdFromDate = timeConversionService.toUtcInstant(syncHoldRequest.holdFromDate),
        holdUntilDate = timeConversionService.toUtcInstant(syncHoldRequest.holdUntilDate as LocalDateTime),
        isReleased = false,
        description = "Test Hold",
        holdType = CreateHoldRequest.HoldType.WHF,
        holdLocation = "LEI",
        amount = syncHoldRequest.amount.toPence(),
      )

      integrationTestHelpers.stubGlAccountsAndSubAccountsForPrisonerAndPrison(
        prisonParentAccount = prisonParentAccount,
        prisonSubAccount = prisonSubaccountUUID,
        prisonSubAccountRef = "2199:${syncHoldRequest.holdType}",
        prisonParentAccountRef = syncHoldRequest.holdLocation,
        prisonerParentAccount = prisonerParentAccount,
        prisonerSubAccount = prisonerSubaccountUUID,
        prisonerSubAccountRef = accountMappingService.mapPrisonerSubAccount(syncHoldRequest.subAccountCode),
        prisonerParentAccountRef = syncHoldRequest.prisonNumber,
      )
      holdsApi.stubPostHoldReturnsError(expectedHoldRequest, statusCode = 400)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isEqualTo(400)
    }

    @Test
    fun `should return a 400 when the General Ledger service returns an error 400`() {
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
        holdTransactionId = 12345,
      )

      generalLedgerApi.stubGetAccountReturnsError(syncHoldRequest.holdLocation, errorCode = 400)
      generalLedgerApi.stubGetAccountReturnsError(syncHoldRequest.prisonNumber, errorCode = 400)

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isEqualTo(400)
    }

    @Test
    fun `should return a 403 when using the incorrect role`() {
      val syncHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
        holdTransactionId = 12345,
      )

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE__INCORRECT_ROLE")))
        .bodyValue(syncHoldRequest)
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  @DisplayName("postHoldRelease")
  inner class PostHoldRelease {

    @Test
    fun `should return a 200 when a hold is released`() {
      //    If the hold has already been released, this is the same behaviour

      val legacyHoldNumber = 123456789L
      val holdsUUID = UUID.randomUUID()
      // Setup the mapping for stubs
      holdsMappingRepository.saveAndFlush(HoldsMapping(legacyHoldNumber = legacyHoldNumber, holdsUuid = holdsUUID))

      val releaseRequest = SyncReleaseHoldRequest(
        releaseDateTime = LocalDateTime.now(),
        releaseTransactionId = 12345,
      )

      val prisonNumber = "AD23451"
      val amount = 500L

      holdsApi.stubReleaseHold(
        prisonNumber = prisonNumber,
        releasedAt = timeConversionService.toUtcInstant(releaseRequest.releaseDateTime),
        amount = amount,
        holdsUUID = holdsUUID,
      )

      val response = webTestClient.post().uri("/sync/holds/$legacyHoldNumber/release")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(releaseRequest)
        .exchange()
        .expectStatus().isOk
        .expectBody<SyncReleasedHoldResponse>()
        .returnResult()
        .responseBody!!

      assertThat(response.prisonNumber).isEqualTo(prisonNumber)
      assertThat(response.releasedAt).isEqualTo(releaseRequest.releaseDateTime)
      assertThat(response.holdNumber).isEqualTo(legacyHoldNumber)
      assertThat(response.amountReleased).isEqualTo(amount.toPounds())
    }

    @Test
    fun `should return a 400 if the hold number is not a Long `() {
      val legacyHoldNumber = "notALong"

      val releaseRequest = SyncReleaseHoldRequest(
        releaseDateTime = LocalDateTime.now(),
        releaseTransactionId = 12345,
      )

      webTestClient.post().uri("/sync/holds/$legacyHoldNumber/release")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(releaseRequest)
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return a 400 if the body is malformed `() {
      val legacyHoldNumber = 123456789L

      val releaseRequest = mapper.writeValueAsString({ "key" to "value" })

      webTestClient.post().uri("/sync/holds/$legacyHoldNumber/release")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(releaseRequest)
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return a 403 if sent the wrong role`() {
      val legacyHoldNumber = 123456789L

      val releaseRequest = SyncReleaseHoldRequest(
        releaseDateTime = LocalDateTime.now(),
        releaseTransactionId = 12345,
      )

      webTestClient.post().uri("/sync/holds/$legacyHoldNumber/release")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .bodyValue(releaseRequest)
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return a 404 if a mapping does not exist for that hold number`() {
      val legacyHoldNumber = 123456789L

      val releaseRequest = SyncReleaseHoldRequest(
        releaseDateTime = LocalDateTime.now(),
        releaseTransactionId = 12345,
      )

      webTestClient.post().uri("/sync/holds/$legacyHoldNumber/release")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(releaseRequest)
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return a 404 if the holds api returns a 404`() {
      val legacyHoldNumber = 123456789L
      val holdsUUID = UUID.randomUUID()
      // Setup the mapping for stubs
      holdsMappingRepository.saveAndFlush(HoldsMapping(legacyHoldNumber = legacyHoldNumber, holdsUuid = holdsUUID))

      val releaseRequest = SyncReleaseHoldRequest(
        releaseDateTime = LocalDateTime.now(),
        releaseTransactionId = 12345,
      )

      holdsApi.stubReleaseHoldNotFound(holdsUUID)

      webTestClient.post().uri("/sync/holds/$legacyHoldNumber/release")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(releaseRequest)
        .exchange()
        .expectStatus().isNotFound
    }
  }

  @Nested
  @DisplayName("migrateHolds")
  inner class MigrateHolds {
    val responseHoldUUID: UUID = UUID.randomUUID()
    val responseHoldTransactionGLUUID: UUID = UUID.randomUUID()
    val prisonNumber = "A1234XZ"

    val prisonHoldAccountNumber = 2199
    val hold = SyncCreateHoldRequest(
      subAccountCode = 2101,
      holdNumber = 12345,
      holdTransactionId = 1234567,
      releaseTransactionId = 1111,
      prisonNumber = prisonNumber,
      createdAt = LocalDateTime.now(),
      createdBy = "",
      holdFromDate = LocalDateTime.now(),
      holdUntilDate = null,
      isReleased = false,
      description = "",
      holdType = "HOA",
      holdLocation = "LEI",
      amount = BigDecimal.valueOf(100),
    )

    @Nested
    inner class WhenAccountsDontResolve {

      @Test
      fun `should return 201 when a hold is migrated - creates a missing sub account where the other exists`() {
        holdsApi.stubMigrateHold(
          holdRequest = hold,
          responseHoldId = responseHoldUUID,
          subAccountRef = HoldResponse.SubAccountRef.CASH,
          holdTransactionId = null,
          releaseTransactionId = null,
        )

        val prisonAccountId = UUID.randomUUID()
        generalLedgerApi.stubGetAccount(
          reference = hold.holdLocation,
          returnUuid = prisonAccountId,
          subAccounts = listOf(
            SubAccountResponse(
              id = UUID.randomUUID(),
              reference = "2199:${hold.holdType}",
              parentAccountId = prisonAccountId,
              createdBy = "TEST USER",
              createdAt = Instant.now(),
            ),
          ),
        )

        val prisonerAccountId = UUID.randomUUID()
        generalLedgerApi.stubGetAccount(
          reference = hold.prisonNumber,
          returnUuid = prisonerAccountId,
          subAccounts = emptyList(),
        )

        generalLedgerApi.stubCreateSubAccount(
          parentId = prisonerAccountId,
          reference = "CASH",
          returnUuid = UUID.randomUUID().toString(),
        )

        webTestClient.post().uri("/migrate/holds")
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
          .bodyValue(hold)
          .exchange()
          .expectStatus().isCreated
          .expectBody<SyncCreateHoldResponse>()
          .returnResult()
          .responseBody!!

        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${hold.holdLocation}")))
        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${hold.prisonNumber}")))

        generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonerAccountId/sub-accounts")))
      }

      @Test
      fun `should return 201 when a hold is migrated and creates all sub account if only the accounts exist`() {
        holdsApi.stubMigrateHold(
          holdRequest = hold,
          responseHoldId = responseHoldUUID,
          subAccountRef = HoldResponse.SubAccountRef.CASH,
          holdTransactionId = null,
          releaseTransactionId = null,
        )

        val prisonAccountId = UUID.randomUUID()
        generalLedgerApi.stubGetAccount(
          reference = hold.holdLocation,
          returnUuid = prisonAccountId,
          subAccounts = emptyList(),
        )

        generalLedgerApi.stubCreateSubAccount(
          parentId = prisonAccountId,
          reference = "2199:${hold.holdType}",
          returnUuid = UUID.randomUUID().toString(),
        )

        val prisonerAccountId = UUID.randomUUID()
        generalLedgerApi.stubGetAccount(
          reference = hold.prisonNumber,
          returnUuid = prisonerAccountId,
          subAccounts = emptyList(),
        )

        generalLedgerApi.stubCreateSubAccount(
          parentId = prisonerAccountId,
          reference = "CASH",
          returnUuid = UUID.randomUUID().toString(),
        )

        webTestClient.post().uri("/migrate/holds")
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
          .bodyValue(hold)
          .exchange()
          .expectStatus().isCreated
          .expectBody<SyncCreateHoldResponse>()
          .returnResult()
          .responseBody!!

        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${hold.holdLocation}")))
        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${hold.prisonNumber}")))

        generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonAccountId/sub-accounts")))
        generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonerAccountId/sub-accounts")))
      }

      @Test
      fun `should return 201 when a hold is migrated and creates all accounts and sub account if none exist`() {
        holdsApi.stubMigrateHold(
          holdRequest = hold,
          responseHoldId = responseHoldUUID,
          subAccountRef = HoldResponse.SubAccountRef.CASH,
          holdTransactionId = null,
          releaseTransactionId = null,
        )

        val prisonAccountId = UUID.randomUUID()
        generalLedgerApi.stubGetAccountNotFound(
          reference = hold.holdLocation,
          scenarioName = "Prison Account",
        )

        generalLedgerApi.stubCreateAccount(
          reference = hold.holdLocation,
          returnUuid = prisonAccountId,
        )

        generalLedgerApi.stubCreateSubAccount(
          parentId = prisonAccountId,
          reference = "2199:${hold.holdType}",
          returnUuid = UUID.randomUUID().toString(),
        )

        val prisonerAccountId = UUID.randomUUID()
        generalLedgerApi.stubGetAccountNotFound(
          reference = hold.prisonNumber,
          scenarioName = "Prisoner Account",
        )

        generalLedgerApi.stubCreateAccount(
          reference = hold.prisonNumber,
          returnUuid = prisonerAccountId,
        )

        generalLedgerApi.stubCreateSubAccount(
          parentId = prisonerAccountId,
          reference = "CASH",
          returnUuid = UUID.randomUUID().toString(),
        )

        webTestClient.post().uri("/migrate/holds")
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
          .bodyValue(hold)
          .exchange()
          .expectStatus().isCreated
          .expectBody<SyncCreateHoldResponse>()
          .returnResult()
          .responseBody!!

        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${hold.holdLocation}")))
        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${hold.prisonNumber}")))

        generalLedgerApi.verify(2, postRequestedFor(urlEqualTo("/accounts")))

        generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonAccountId/sub-accounts")))
        generalLedgerApi.verify(1, postRequestedFor(urlEqualTo("/accounts/$prisonerAccountId/sub-accounts")))
      }
    }

    @Nested
    inner class WhenAccountsDoResolve {
      val prisonAccountId: UUID = UUID.randomUUID()
      val prisonerAccountId: UUID = UUID.randomUUID()

      @BeforeEach
      fun setup() {
        generalLedgerApi.resetAll()

        // Stub prison account and sub account
        generalLedgerApi.stubGetAccount(
          reference = hold.holdLocation,
          returnUuid = prisonAccountId,
          subAccounts = listOf(
            SubAccountResponse(
              id = UUID.randomUUID(),
              reference = "2199:${hold.holdType}",
              parentAccountId = prisonAccountId,
              createdBy = "TEST USER",
              createdAt = Instant.now(),
            ),
          ),
          scenarioName = "Prison Account",
        )

        // Stub prisoner account and sub account
        generalLedgerApi.stubGetAccount(
          reference = hold.prisonNumber,
          returnUuid = prisonerAccountId,
          subAccounts = listOf(
            SubAccountResponse(
              id = UUID.randomUUID(),
              reference = "CASH",
              parentAccountId = prisonerAccountId,
              createdBy = "TEST USER",
              createdAt = Instant.now(),
            ),
          ),
          scenarioName = "Prisoner Account",
        )
      }

      @Test
      fun `should return 201 when a hold is migrated and is able to resolve the prisoner's and prison's sub account`() {
        holdsApi.stubMigrateHold(
          holdRequest = hold,
          responseHoldId = responseHoldUUID,
          subAccountRef = HoldResponse.SubAccountRef.CASH,
          holdTransactionId = null,
          releaseTransactionId = null,
        )

        webTestClient.post().uri("/migrate/holds")
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
          .bodyValue(hold)
          .exchange()
          .expectStatus().isCreated
          .expectBody<SyncCreateHoldResponse>()
          .returnResult()
          .responseBody!!

        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${hold.holdLocation}")))
        generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=${hold.prisonNumber}")))
      }

      @Test
      fun `should return 201 when a hold is migrated twice`() {
        holdsApi.stubMigrateHold(
          holdRequest = hold,
          responseHoldId = responseHoldUUID,
          subAccountRef = HoldResponse.SubAccountRef.CASH,
        )

        repeat(2) {
          val response = webTestClient.post().uri("/migrate/holds")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
            .bodyValue(hold)
            .exchange()
            .expectStatus().isCreated
            .expectBody<SyncCreateHoldResponse>()
            .returnResult()
            .responseBody!!

          assertThat(response.holdNumber).isEqualTo(hold.holdNumber)
          assertThat(response.holdUuid).isEqualTo(responseHoldUUID)
        }
      }

      @Test
      fun `should return 201 when a hold is migrated and send any available GL mappings`() {
        // Sync hold
        integrationTestHelpers.stubAndCreateTransaction(
          transactionId = hold.holdTransactionId,
          prisonNumber = hold.prisonNumber,
          caseloadId = hold.holdLocation,
          transactionType = hold.holdType,
          transactionUUID = responseHoldTransactionGLUUID,
          prisonAccountCode = prisonHoldAccountNumber,
          prisonerAccountCode = hold.subAccountCode,
          prisonAccountRef = "CASH",
        )

        holdsApi.stubMigrateHold(
          holdRequest = hold,
          responseHoldId = responseHoldUUID,
          subAccountRef = HoldResponse.SubAccountRef.CASH,
          holdTransactionId = responseHoldTransactionGLUUID,
        )

        val response = webTestClient.post().uri("/migrate/holds")
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
          .bodyValue(hold)
          .exchange()
          .expectStatus().isCreated
          .expectBody<SyncCreateHoldResponse>()
          .returnResult()
          .responseBody!!

        assertThat(response.holdNumber).isEqualTo(hold.holdNumber)
        assertThat(response.holdUuid).isEqualTo(responseHoldUUID)
      }

      @Test
      fun `should return 400  when hold is released but doesn't have a released transaction`() {
        val hold = SyncCreateHoldRequest(
          subAccountCode = 2101,
          holdNumber = 12345,
          holdTransactionId = 1234567,
          prisonNumber = "A1234XZ",
          createdAt = LocalDateTime.now(),
          createdBy = "",
          holdFromDate = LocalDateTime.now(),
          holdUntilDate = null,
          isReleased = true,
          description = "",
          holdType = "HOA",
          holdLocation = "LEI",
          amount = BigDecimal.valueOf(100),
        )

        webTestClient.post().uri("/migrate/holds")
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
          .bodyValue(hold)
          .exchange()
          .expectStatus().isBadRequest
          .expectBody<ErrorResponse>()
          .returnResult()
          .responseBody!!
      }

      @Test
      fun `should return 502 when the general ledger service returns an error`() {
        generalLedgerApi.stubGetAccountReturnsError(
          reference = hold.prisonNumber,
          errorCode = 500,
        )

        webTestClient.post().uri("/migrate/holds")
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
          .bodyValue(hold)
          .exchange()
          .expectStatus().isEqualTo(502)
          .expectBody<ErrorResponse>()
      }

      @Test
      fun `should return 502 when hold service returns an error`() {
        val accountId = UUID.randomUUID()
        generalLedgerApi.stubGetAccount(
          reference = hold.prisonNumber,
          returnUuid = accountId,
          subAccounts = listOf(
            SubAccountResponse(
              id = accountId,
              reference = "CASH",
              parentAccountId = UUID.randomUUID(),
              createdBy = "TEST USER",
              createdAt = Instant.now(),
            ),
          ),
        )
        holdsApi.stubMigrateHoldReturnsError()

        webTestClient.post().uri("/migrate/holds")
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
          .bodyValue(hold)
          .exchange()
          .expectStatus().isEqualTo(502)
          .expectBody<ErrorResponse>()
      }

      @Test
      fun `should return a 403 if sent the wrong role`() {
        webTestClient.post().uri("/migrate/holds")
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
          .bodyValue(hold)
          .exchange()
          .expectStatus().isEqualTo(403)
          .expectBody<ErrorResponse>()
      }
    }
  }
}
