package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.MigratedGeneralLedgerBalancePayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.MigratedPrisonerBalancePayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.MigratedGeneralLedgerBalancePayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.MigratedPrisonerBalancePayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@ExtendWith(MockitoExtension::class)
class MigrationPayloadCaptureServiceTest {

  @Mock
  private lateinit var objectMapper: ObjectMapper

  @Mock
  private lateinit var generalLedgerPayloadRepository: MigratedGeneralLedgerBalancePayloadRepository

  @Mock
  private lateinit var prisonerBalancePayloadRepository: MigratedPrisonerBalancePayloadRepository

  @InjectMocks
  private lateinit var migrationPayloadCaptureService: MigrationPayloadCaptureService

  @Captor
  private lateinit var glPayloadCaptor: ArgumentCaptor<MigratedGeneralLedgerBalancePayload>

  @Captor
  private lateinit var pbPayloadCaptor: ArgumentCaptor<MigratedPrisonerBalancePayload>

  private lateinit var dummyGeneralLedgerBalancesSyncRequest: GeneralLedgerBalancesSyncRequest
  private lateinit var dummyPrisonerBalancesSyncRequest: PrisonerBalancesSyncRequest
  private val dummyPrisonId = "LEI"
  private val dummyPrisonerNumber = "A1234AA"

  @BeforeEach
  fun setupGlobalDummies() {
    dummyGeneralLedgerBalancesSyncRequest = GeneralLedgerBalancesSyncRequest(
      accountBalances = listOf(
        GeneralLedgerPointInTimeBalance(
          accountCode = 1101,
          balance = BigDecimal("500.00"),
          asOfTimestamp = LocalDateTime.now(),
        ),
      ),
    )
    dummyPrisonerBalancesSyncRequest = PrisonerBalancesSyncRequest(
      accountBalances = listOf(
        PrisonerAccountPointInTimeBalance(
          prisonId = "LEI",
          accountCode = 2101,
          balance = BigDecimal("100.00"),
          holdBalance = BigDecimal("10.00"),
          asOfTimestamp = LocalDateTime.now(),
          transactionId = 12345L,
        ),
      ),
    )
  }

  @Nested
  @DisplayName("captureGeneralLedgerMigrationRequest")
  inner class CaptureGeneralLedgerMigrationRequest {

    @BeforeEach
    fun setupSaveMock() {
      `when`(generalLedgerPayloadRepository.save(any())).thenAnswer { invocation ->
        val payloadToSave = invocation.getArgument<MigratedGeneralLedgerBalancePayload>(0)
        MigratedGeneralLedgerBalancePayload(
          id = 101L,
          timestamp = payloadToSave.timestamp,
          prisonId = payloadToSave.prisonId,
          body = payloadToSave.body,
        )
      }
    }

    @Test
    fun `should serialize and store GeneralLedgerBalancesSyncRequest`() {
      val expectedJson = """{"some":"json","from":"GeneralLedgerBalancesSyncRequest"}"""
      `when`(objectMapper.writeValueAsString(dummyGeneralLedgerBalancesSyncRequest)).thenReturn(expectedJson)

      val result = migrationPayloadCaptureService.captureGeneralLedgerMigrationRequest(dummyPrisonId, dummyGeneralLedgerBalancesSyncRequest)

      verify(generalLedgerPayloadRepository, times(1)).save(glPayloadCaptor.capture())
      val capturedPayloadSentToRepo = glPayloadCaptor.value

      assertThat(result.id).isEqualTo(101L)
      assertThat(capturedPayloadSentToRepo.prisonId).isEqualTo(dummyPrisonId)
      assertThat(capturedPayloadSentToRepo.body).isEqualTo(expectedJson)
      assertThat(capturedPayloadSentToRepo.timestamp).isCloseTo(Instant.now(), Assertions.within(2, ChronoUnit.SECONDS))
    }
  }

  @Nested
  @DisplayName("capturePrisonerMigrationRequest")
  inner class CapturePrisonerMigrationRequest {

    @BeforeEach
    fun setupSaveMock() {
      `when`(prisonerBalancePayloadRepository.save(any())).thenAnswer { invocation ->
        val payloadToSave = invocation.getArgument<MigratedPrisonerBalancePayload>(0)
        MigratedPrisonerBalancePayload(
          id = 201L,
          timestamp = payloadToSave.timestamp,
          prisonNumber = payloadToSave.prisonNumber,
          body = payloadToSave.body,
        )
      }
    }

    @Test
    fun `should serialize and store PrisonerBalancesSyncRequest`() {
      val expectedJson = """{"some":"json","from":"PrisonerBalancesSyncRequest"}"""
      `when`(objectMapper.writeValueAsString(dummyPrisonerBalancesSyncRequest)).thenReturn(expectedJson)

      val result = migrationPayloadCaptureService.capturePrisonerMigrationRequest(dummyPrisonerNumber, dummyPrisonerBalancesSyncRequest)

      verify(prisonerBalancePayloadRepository, times(1)).save(pbPayloadCaptor.capture())
      val capturedPayloadSentToRepo = pbPayloadCaptor.value

      assertThat(result.id).isEqualTo(201L)
      assertThat(capturedPayloadSentToRepo.prisonNumber).isEqualTo(dummyPrisonerNumber)
      assertThat(capturedPayloadSentToRepo.body).isEqualTo(expectedJson)
      assertThat(capturedPayloadSentToRepo.timestamp).isCloseTo(Instant.now(), Assertions.within(2, ChronoUnit.SECONDS))
    }
  }
}
