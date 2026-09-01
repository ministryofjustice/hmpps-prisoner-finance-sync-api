package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
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

    @Mock
    private lateinit var holdsApiClient: HoldsApiClient

    @Spy
    private lateinit var mockedTimeConversionService: TimeConversionService

    @Mock
    private lateinit var holdsMappingRepository: HoldsMappingRepository

    @InjectMocks
    private lateinit var holdsService: HoldsService

    val timeConversionService = TimeConversionService()

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

      whenever(holdsApiClient.postHold(createHoldRequest)).thenReturn(createHoldResponse)
      whenever(holdsMappingRepository.save(HoldsMapping(legacyHoldNumber = syncCreateHoldRequest.holdNumber, holdsUuid = createHoldResponse.id))).thenReturn(HoldsMapping(id = 1L, legacyHoldNumber = syncCreateHoldRequest.holdNumber, holdsUuid = createHoldResponse.id))

      val createdHold = holdsService.createHold(syncCreateHoldRequest)

      assertThat(createdHold).isEqualTo(createHoldResponse)
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

      whenever(holdsApiClient.postHold(createHoldRequest)).thenThrow(
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
