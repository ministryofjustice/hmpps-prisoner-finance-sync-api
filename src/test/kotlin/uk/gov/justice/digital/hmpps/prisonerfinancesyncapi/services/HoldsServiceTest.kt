package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.HoldsApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.HoldsMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.HoldsMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@DisplayName("Holds Service Test")
class HoldsServiceTest {

  @Nested
  @DisplayName("Create Hold")
  inner class CreateHold {

    @Spy
    private lateinit var mockedTimeConversionService: TimeConversionService

    @Mock
    private lateinit var holdsApiClient: HoldsApiClient

    @Mock
    private lateinit var holdsMappingRepository: HoldsMappingRepository

    @Spy
    private lateinit var idempotencyService: GeneralLedgerIdempotencyService

    @Mock
    private lateinit var accountResolver: GeneralLedgerAccountResolver

    @Spy
    private lateinit var accountMapping: LedgerAccountMappingService

    @Spy
    private lateinit var requestCache: InMemoryAccountCache

    private lateinit var holdsService: HoldsService

    @BeforeEach
    fun setup() {
      // InjectMocks does not work for some reason
      holdsService = HoldsService(
        timeConversionService,
        holdsApiClient,
        holdsMappingRepository,
        idempotencyService,
        accountResolver,
        requestCache,
        accountMapping,
      )
    }

    val timeConversionService = TimeConversionService()

    val prisonSubaccountUUID = UUID.randomUUID()
    val prisonerSubaccountUUID = UUID.randomUUID()

    fun mockAccountResolver(
      syncCreateHoldRequest: SyncCreateHoldRequest,
      prisonSubaccountUUID: UUID,
      prisonerSubaccountUUID: UUID,
    ) {
      whenever(
        accountResolver.resolveSubAccount(
          prisonId = eq(syncCreateHoldRequest.holdLocation),
          offenderId = eq(""),
          accountCode = eq(2199),
          transactionType = eq(syncCreateHoldRequest.holdType),
          parentCache = any(),
        ),
      ).thenReturn(
        prisonSubaccountUUID,
      )

      whenever(
        accountResolver.resolveSubAccount(
          prisonId = eq(""),
          offenderId = eq(syncCreateHoldRequest.prisonNumber),
          accountCode = eq(syncCreateHoldRequest.subAccountCode),
          transactionType = eq(syncCreateHoldRequest.holdType),
          parentCache = any(),
        ),
      ).thenReturn(
        prisonerSubaccountUUID,
      )
    }

    @Test
    fun `should send the hold request to the hold service, store the mapping and return the created hold`() {
      val holdsCreatedAt = LocalDateTime.now()
      val holdsUntilDate = LocalDateTime.now().plusDays(1)

      val syncCreateHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = holdsCreatedAt,
        createdBy = "USER",
        holdFromDate = holdsCreatedAt,
        holdUntilDate = holdsUntilDate,
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
        holdTransactionId = 12345,
      )

      val holdsCreatedAtUTC = timeConversionService.toUtcInstant(holdsCreatedAt)
      val holdsUntilDateUTC = timeConversionService.toUtcInstant(holdsUntilDate)

      val createHoldRequest = CreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountRef = CreateHoldRequest.SubAccountRef.CASH,
        legacyHoldNumber = 123456789,
        createdAt = holdsCreatedAtUTC,
        createdBy = "USER",
        holdFromDate = holdsCreatedAtUTC,
        holdUntilDate = holdsUntilDateUTC,
        isReleased = false,
        description = "Test Hold",
        holdType = CreateHoldRequest.HoldType.WHF,
        holdLocation = "LEI",
        amount = BigDecimal("99.99").toPence(),
        prisonerSubAccountId = prisonerSubaccountUUID,
        prisonSubAccountId = prisonSubaccountUUID,
        holdLegacyTransactionId = syncCreateHoldRequest.holdTransactionId,
      )

      val createHoldResponseId = UUID.randomUUID()

      val createHoldResponse = HoldResponse(
        id = createHoldResponseId,
        prisonNumber = "AD23451",
        subAccountRef = HoldResponse.SubAccountRef.CASH,
        legacyHoldNumber = 123456789,
        createdAt = holdsCreatedAtUTC,
        createdBy = "USER",
        holdFromDate = holdsCreatedAtUTC,
        holdUntilDate = holdsUntilDateUTC,
        isReleased = false,
        description = "Test Hold",
        holdType = HoldResponse.HoldType.WHF,
        holdLocation = "LEI",
        amount = BigDecimal("99.99").toPence(),
      )

      val syncCreateHoldResponse = SyncCreateHoldResponse(
        holdNumber = createHoldRequest.legacyHoldNumber,
        holdUuid = createHoldResponseId,
      )

      mockAccountResolver(syncCreateHoldRequest, prisonSubaccountUUID, prisonerSubaccountUUID)

      whenever(
        holdsApiClient.postHold(
          request = eq(createHoldRequest),
          idempotencyKey = any(),
        ),
      ).thenReturn(createHoldResponse)

      whenever(
        holdsMappingRepository.save(
          HoldsMapping(legacyHoldNumber = syncCreateHoldRequest.holdNumber, holdsUuid = createHoldResponseId),
        ),
      )
        .thenReturn(
          HoldsMapping(id = 1L, legacyHoldNumber = syncCreateHoldRequest.holdNumber, holdsUuid = createHoldResponseId),
        )

      val createdHold = holdsService.createHold(syncCreateHoldRequest)

      assertThat(createdHold.holdUuid).isEqualTo(syncCreateHoldResponse.holdUuid)
      assertThat(createdHold.holdNumber).isEqualTo(syncCreateHoldResponse.holdNumber)
    }

    @Test
    fun `should not write the mapping to the repository if the holds api responds with 409`() {
      val holdsCreatedAt = LocalDateTime.now()
      val holdsUntilDate = LocalDateTime.now().plusDays(1)

      val syncCreateHoldRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2101,
        holdNumber = 123456789,
        createdAt = holdsCreatedAt,
        createdBy = "USER",
        holdFromDate = holdsCreatedAt,
        holdUntilDate = holdsUntilDate,
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99"),
        holdTransactionId = 12345,
      )

      val holdsCreatedAtUTC = timeConversionService.toUtcInstant(holdsCreatedAt)
      val holdsUntilDateUTC = timeConversionService.toUtcInstant(holdsUntilDate)

      val createHoldRequest = CreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountRef = CreateHoldRequest.SubAccountRef.CASH,
        legacyHoldNumber = 123456789,
        createdAt = holdsCreatedAtUTC,
        createdBy = "USER",
        holdFromDate = holdsCreatedAtUTC,
        holdUntilDate = holdsUntilDateUTC,
        isReleased = false,
        description = "Test Hold",
        holdType = CreateHoldRequest.HoldType.WHF,
        holdLocation = "LEI",
        amount = BigDecimal("99.99").toPence(),
        prisonerSubAccountId = prisonerSubaccountUUID,
        prisonSubAccountId = prisonSubaccountUUID,
        holdLegacyTransactionId = syncCreateHoldRequest.holdTransactionId,
      )

      val createHoldResponse = HoldResponse(
        id = UUID.randomUUID(),
        prisonNumber = "AD23451",
        subAccountRef = HoldResponse.SubAccountRef.CASH,
        legacyHoldNumber = 123456789,
        createdAt = holdsCreatedAtUTC,
        createdBy = "USER",
        holdFromDate = holdsCreatedAtUTC,
        holdUntilDate = holdsUntilDateUTC,
        isReleased = false,
        description = "Test Hold",
        holdType = HoldResponse.HoldType.WHF,
        holdLocation = "LEI",
        amount = BigDecimal("99.99").toPence(),
      )

      val responseBytes = createHoldResponse.id.toString().toByteArray(StandardCharsets.UTF_8)

      mockAccountResolver(syncCreateHoldRequest, prisonSubaccountUUID, prisonerSubaccountUUID)

      whenever(
        holdsApiClient.postHold(
          request = eq(createHoldRequest),
          idempotencyKey = any(),
        ),
      ).thenThrow(
        WebClientResponseException(
          409,
          "Conflict",
          null,
          responseBytes,
          StandardCharsets.UTF_8,
        ),
      )

      assertThatThrownBy { holdsService.createHold(syncCreateHoldRequest) }
        .isInstanceOf(WebClientResponseException::class.java)
        .hasMessageContaining("Conflict")
        .extracting { (it as WebClientResponseException).statusCode.value() }
        .isEqualTo(409)

      verify(holdsMappingRepository, times(0)).save(any())
    }
  }
}
