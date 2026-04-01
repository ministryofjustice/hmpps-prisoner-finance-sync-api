package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync

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
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SyncPayloadCaptureServiceTest {

  @Mock
  private lateinit var nomisSyncPayloadRepository: NomisSyncPayloadRepository

  @Mock
  private lateinit var objectMapper: ObjectMapper

  @Mock
  private lateinit var timeConversionService: TimeConversionService

  @InjectMocks
  private lateinit var syncPayloadCaptureService: SyncPayloadCaptureService

  @Captor
  private lateinit var nomisSyncPayloadCaptor: ArgumentCaptor<NomisSyncPayload>

  private lateinit var dummyOffenderTransactionRequest: SyncOffenderTransactionRequest
  private lateinit var dummyGeneralLedgerTransactionRequest: SyncGeneralLedgerTransactionRequest

  @BeforeEach
  fun setupGlobalDummies() {
    dummyOffenderTransactionRequest = SyncOffenderTransactionRequest(
      transactionId = 19228028,
      requestId = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef"),
      caseloadId = "GMI",
      transactionTimestamp = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      createdBy = "JD12345",
      createdByDisplayName = "J Doe",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1015388L,
          offenderDisplayId = "AA001AA",
          offenderBookingId = 455987L,
          subAccountType = "REG",
          postingType = "DR",
          type = "OT",
          description = "Sub-Account Transfer",
          amount = BigDecimal("162.00"),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = BigDecimal("162.00")),
            GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = BigDecimal("162.00")),
          ),
        ),
      ),
    )
    dummyGeneralLedgerTransactionRequest = SyncGeneralLedgerTransactionRequest(
      transactionId = 19228029,
      requestId = UUID.fromString("c3d4e5f6-a7b8-9012-3456-7890abcdef01"),
      description = "General Ledger Account Transfer",
      reference = "REF12345",
      caseloadId = "MDI",
      transactionType = "GJ",
      transactionTimestamp = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      createdBy = "JD12346",
      createdByDisplayName = "J. Smith",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      generalLedgerEntries = listOf(
        GeneralLedgerEntry(entrySequence = 1, code = 1101, postingType = "DR", amount = BigDecimal("50.00")),
        GeneralLedgerEntry(entrySequence = 2, code = 2503, postingType = "CR", amount = BigDecimal("50.00")),
      ),
    )
  }

  @Nested
  @DisplayName("captureAndStoreRequest")
  inner class CaptureAndStoreRequest {

    private val mockedSynchronizedTransactionId = UUID.fromString("a1a1a1a1-b1b1-c1c1-d1d1-e1e1e1e1e1e1")
    private val mockedTransactionInstant = Instant.now().minus(1, ChronoUnit.DAYS)

    @BeforeEach
    fun setupSaveMock() {
      `when`(timeConversionService.toUtcInstant(any())).thenReturn(mockedTransactionInstant)

      `when`(nomisSyncPayloadRepository.save(any())).thenAnswer { invocation ->
        val payloadToSave = invocation.getArgument<NomisSyncPayload>(0)
        NomisSyncPayload(
          id = 1L,
          timestamp = payloadToSave.timestamp,
          legacyTransactionId = payloadToSave.legacyTransactionId,
          requestId = payloadToSave.requestId,
          synchronizedTransactionId = payloadToSave.synchronizedTransactionId,
          caseloadId = payloadToSave.caseloadId,
          requestTypeIdentifier = payloadToSave.requestTypeIdentifier,
          body = payloadToSave.body,
          transactionType = "TEST",
          transactionTimestamp = payloadToSave.transactionTimestamp,
        )
      }
    }

    @Test
    fun `should serialize and store SyncOffenderTransactionRequest with a newly generated synchronized ID`() {
      val expectedJson = """{"some":"json","from":"offenderTransaction"}"""
      `when`(objectMapper.writeValueAsString(dummyOffenderTransactionRequest)).thenReturn(expectedJson)

      val result = syncPayloadCaptureService.captureAndStoreRequest(dummyOffenderTransactionRequest)

      verify(nomisSyncPayloadRepository, times(1)).save(nomisSyncPayloadCaptor.capture())
      val capturedPayloadSentToRepo = nomisSyncPayloadCaptor.value

      assertThat(result.id).isEqualTo(1L)
      assertThat(capturedPayloadSentToRepo.synchronizedTransactionId).isNotNull()

      assertThat(capturedPayloadSentToRepo.body).isEqualTo(expectedJson)
      assertThat(capturedPayloadSentToRepo.legacyTransactionId).isEqualTo(dummyOffenderTransactionRequest.transactionId)
      assertThat(capturedPayloadSentToRepo.requestId).isEqualTo(dummyOffenderTransactionRequest.requestId)
      assertThat(capturedPayloadSentToRepo.caseloadId).isEqualTo(dummyOffenderTransactionRequest.caseloadId)
      assertThat(capturedPayloadSentToRepo.requestTypeIdentifier).isEqualTo(SyncOffenderTransactionRequest::class.simpleName)
      assertThat(capturedPayloadSentToRepo.timestamp).isCloseTo(Instant.now(), Assertions.within(2, ChronoUnit.SECONDS))
      assertThat(capturedPayloadSentToRepo.transactionTimestamp).isEqualTo(mockedTransactionInstant)
    }

    @Test
    fun `should serialize and store SyncOffenderTransactionRequest using a provided synchronized ID`() {
      val expectedJson = """{"some":"json","from":"offenderTransaction"}"""
      `when`(objectMapper.writeValueAsString(dummyOffenderTransactionRequest)).thenReturn(expectedJson)

      val result = syncPayloadCaptureService.captureAndStoreRequest(dummyOffenderTransactionRequest, mockedSynchronizedTransactionId)

      verify(nomisSyncPayloadRepository, times(1)).save(nomisSyncPayloadCaptor.capture())
      val capturedPayloadSentToRepo = nomisSyncPayloadCaptor.value

      assertThat(result.id).isEqualTo(1L)
      assertThat(capturedPayloadSentToRepo.synchronizedTransactionId).isEqualTo(mockedSynchronizedTransactionId)

      assertThat(capturedPayloadSentToRepo.body).isEqualTo(expectedJson)
      assertThat(capturedPayloadSentToRepo.legacyTransactionId).isEqualTo(dummyOffenderTransactionRequest.transactionId)
      assertThat(capturedPayloadSentToRepo.requestId).isEqualTo(dummyOffenderTransactionRequest.requestId)
      assertThat(capturedPayloadSentToRepo.caseloadId).isEqualTo(dummyOffenderTransactionRequest.caseloadId)
      assertThat(capturedPayloadSentToRepo.requestTypeIdentifier).isEqualTo(SyncOffenderTransactionRequest::class.simpleName)
      assertThat(capturedPayloadSentToRepo.timestamp).isCloseTo(Instant.now(), Assertions.within(2, ChronoUnit.SECONDS))
      assertThat(capturedPayloadSentToRepo.transactionTimestamp).isEqualTo(mockedTransactionInstant)
    }

    @Test
    fun `should serialize and store SyncGeneralLedgerTransactionRequest with a newly generated synchronized ID`() {
      val expectedJson = """{"some":"json","from":"generalLedgerTransaction"}"""
      `when`(objectMapper.writeValueAsString(dummyGeneralLedgerTransactionRequest)).thenReturn(expectedJson)

      val result = syncPayloadCaptureService.captureAndStoreRequest(dummyGeneralLedgerTransactionRequest)

      verify(nomisSyncPayloadRepository, times(1)).save(nomisSyncPayloadCaptor.capture())
      val capturedPayloadSentToRepo = nomisSyncPayloadCaptor.value

      assertThat(result.id).isEqualTo(1L)
      assertThat(capturedPayloadSentToRepo.synchronizedTransactionId).isNotNull()

      assertThat(capturedPayloadSentToRepo.body).isEqualTo(expectedJson)
      assertThat(capturedPayloadSentToRepo.legacyTransactionId).isEqualTo(dummyGeneralLedgerTransactionRequest.transactionId)
      assertThat(capturedPayloadSentToRepo.requestId).isEqualTo(dummyGeneralLedgerTransactionRequest.requestId)
      assertThat(capturedPayloadSentToRepo.caseloadId).isEqualTo(dummyGeneralLedgerTransactionRequest.caseloadId)
      assertThat(capturedPayloadSentToRepo.requestTypeIdentifier).isEqualTo(SyncGeneralLedgerTransactionRequest::class.simpleName)
      assertThat(capturedPayloadSentToRepo.timestamp).isCloseTo(Instant.now(), Assertions.within(2, ChronoUnit.SECONDS))
      assertThat(capturedPayloadSentToRepo.transactionTimestamp).isEqualTo(mockedTransactionInstant)
    }

    @Test
    fun `should serialize and store SyncGeneralLedgerTransactionRequest using a provided synchronized ID`() {
      val expectedJson = """{"some":"json","from":"generalLedgerTransaction"}"""
      `when`(objectMapper.writeValueAsString(dummyGeneralLedgerTransactionRequest)).thenReturn(expectedJson)

      val result = syncPayloadCaptureService.captureAndStoreRequest(dummyGeneralLedgerTransactionRequest, mockedSynchronizedTransactionId)

      verify(nomisSyncPayloadRepository, times(1)).save(nomisSyncPayloadCaptor.capture())
      val capturedPayloadSentToRepo = nomisSyncPayloadCaptor.value

      assertThat(result.id).isEqualTo(1L)
      assertThat(capturedPayloadSentToRepo.synchronizedTransactionId).isEqualTo(mockedSynchronizedTransactionId)

      assertThat(capturedPayloadSentToRepo.body).isEqualTo(expectedJson)
      assertThat(capturedPayloadSentToRepo.legacyTransactionId).isEqualTo(dummyGeneralLedgerTransactionRequest.transactionId)
      assertThat(capturedPayloadSentToRepo.requestId).isEqualTo(dummyGeneralLedgerTransactionRequest.requestId)
      assertThat(capturedPayloadSentToRepo.caseloadId).isEqualTo(dummyGeneralLedgerTransactionRequest.caseloadId)
      assertThat(capturedPayloadSentToRepo.requestTypeIdentifier).isEqualTo(SyncGeneralLedgerTransactionRequest::class.simpleName)
      assertThat(capturedPayloadSentToRepo.timestamp).isCloseTo(Instant.now(), Assertions.within(2, ChronoUnit.SECONDS))
      assertThat(capturedPayloadSentToRepo.transactionTimestamp).isEqualTo(mockedTransactionInstant)
    }
  }
}
