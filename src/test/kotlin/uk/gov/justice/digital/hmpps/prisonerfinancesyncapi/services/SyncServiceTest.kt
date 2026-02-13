package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerSyncService
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SyncServiceTest {

  @Mock
  private lateinit var ledgerSyncService: LedgerSyncService

  @Mock
  private lateinit var requestCaptureService: RequestCaptureService

  @Mock
  private lateinit var syncQueryService: SyncQueryService

  @Mock
  private lateinit var jsonComparator: JsonComparator

  @Mock
  private lateinit var objectMapper: ObjectMapper

  @InjectMocks
  private lateinit var syncService: SyncService

  private lateinit var dummyGeneralLedgerTransactionRequest: SyncGeneralLedgerTransactionRequest
  private lateinit var dummyStoredPayload: NomisSyncPayload

  @BeforeEach
  fun setupGlobalDummies() {
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

    dummyStoredPayload = NomisSyncPayload(
      id = 1L,
      timestamp = Instant.now(),
      legacyTransactionId = dummyGeneralLedgerTransactionRequest.transactionId,
      requestId = dummyGeneralLedgerTransactionRequest.requestId,
      caseloadId = dummyGeneralLedgerTransactionRequest.caseloadId,
      requestTypeIdentifier = SyncGeneralLedgerTransactionRequest::class.simpleName,
      synchronizedTransactionId = UUID.fromString("a1a1a1a1-b1b1-c1c1-d1d1-e1e1e1e1e1e1"),
      body = "{}",
      transactionTimestamp = Instant.now(),
    )
  }

  @Nested
  @DisplayName("syncTransaction")
  inner class SyncTransactionTests {

    @Test
    fun `should return PROCESSED if a request with the same requestId already exists`() {
      whenever(syncQueryService.findByRequestId(any())).thenReturn(dummyStoredPayload)

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.PROCESSED)
      assertThat(result.requestId).isEqualTo(dummyGeneralLedgerTransactionRequest.requestId)
      assertThat(result.synchronizedTransactionId).isEqualTo(dummyStoredPayload.synchronizedTransactionId)
      verify(syncQueryService, times(1)).findByRequestId(any())
      verify(ledgerSyncService, times(0)).syncGeneralLedgerTransaction(any())
      verify(requestCaptureService, times(0)).captureAndStoreRequest(any(), anyOrNull())
    }

    @Test
    fun `should return CREATED if neither requestId nor transactionId exists`() {
      whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
      whenever(syncQueryService.findByLegacyTransactionId(any())).thenReturn(null)
      whenever(requestCaptureService.captureAndStoreRequest(any(), anyOrNull())).thenReturn(dummyStoredPayload)
      whenever(ledgerSyncService.syncGeneralLedgerTransaction(any())).thenReturn(dummyStoredPayload.synchronizedTransactionId)

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.CREATED)
      assertThat(result.requestId).isEqualTo(dummyGeneralLedgerTransactionRequest.requestId)
      assertThat(result.synchronizedTransactionId).isEqualTo(dummyStoredPayload.synchronizedTransactionId)
      verify(syncQueryService, times(1)).findByRequestId(any())
      verify(syncQueryService, times(1)).findByLegacyTransactionId(any())
      verify(ledgerSyncService, times(1)).syncGeneralLedgerTransaction(any())
      verify(requestCaptureService, times(1)).captureAndStoreRequest(any(), eq(dummyStoredPayload.synchronizedTransactionId))
    }

    @Test
    fun `should retry and succeed when DataIntegrityViolationException occurs`() {
      whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
      whenever(syncQueryService.findByLegacyTransactionId(any())).thenReturn(null)

      whenever(ledgerSyncService.syncGeneralLedgerTransaction(any()))
        .thenThrow(DataIntegrityViolationException("Race condition"))
        .thenReturn(dummyStoredPayload.synchronizedTransactionId)

      whenever(requestCaptureService.captureAndStoreRequest(any(), anyOrNull())).thenReturn(dummyStoredPayload)

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.CREATED)
      verify(ledgerSyncService, times(2)).syncGeneralLedgerTransaction(any())
    }

    @Test
    fun `should fail and log error if retry also fails`() {
      whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
      whenever(syncQueryService.findByLegacyTransactionId(any())).thenReturn(null)

      whenever(ledgerSyncService.syncGeneralLedgerTransaction(any()))
        .thenThrow(DataIntegrityViolationException("Race condition"))
        .thenThrow(RuntimeException("Retry failed"))

      whenever(objectMapper.writeValueAsString(any())).thenReturn("{}")

      assertThrows(RuntimeException::class.java) {
        syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)
      }
      verify(ledgerSyncService, times(2)).syncGeneralLedgerTransaction(any())
    }

    @Test
    fun `should throw IllegalArgumentException for unknown request types`() {
      whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
      whenever(syncQueryService.findByLegacyTransactionId(any())).thenReturn(null)

      val unknownRequest = Mockito.mock(uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncRequest::class.java)
      whenever(unknownRequest.requestId).thenReturn(UUID.randomUUID())
      whenever(unknownRequest.transactionId).thenReturn(1L)

      assertThrows(IllegalArgumentException::class.java) {
        syncService.syncTransaction(unknownRequest)
      }
    }

    @Test
    fun `should handle JSON serialization error during error logging`() {
      whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
      whenever(syncQueryService.findByLegacyTransactionId(any())).thenReturn(null)
      whenever(ledgerSyncService.syncGeneralLedgerTransaction(any())).thenThrow(RuntimeException("Boom"))

      whenever(objectMapper.writeValueAsString(any())).thenThrow(RuntimeException("Serialization failed"))

      assertThrows(RuntimeException::class.java) {
        syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)
      }
      verify(objectMapper, times(1)).writeValueAsString(any())
    }

    @Test
    fun `should return PROCESSED if the body JSON is identical to existing transaction`() {
      whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
      whenever(syncQueryService.findByLegacyTransactionId(any())).thenReturn(dummyStoredPayload)

      val newBodyJson = "{\"transactionId\":19228029}"
      whenever(objectMapper.writeValueAsString(any())).thenReturn(newBodyJson)
      whenever(jsonComparator.areJsonBodiesEqual(any(), any())).thenReturn(true)

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.PROCESSED)
      assertThat(result.requestId).isEqualTo(dummyGeneralLedgerTransactionRequest.requestId)
      assertThat(result.synchronizedTransactionId).isEqualTo(dummyStoredPayload.synchronizedTransactionId)
    }

    @Test
    fun `should return UPDATED if the body JSON is different to existing transaction`() {
      whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
      whenever(syncQueryService.findByLegacyTransactionId(any())).thenReturn(dummyStoredPayload)

      val differentBodyJson = "{\"transactionId\":19228029, \"diff\": true}"
      val updatedPayload = dummyStoredPayload.copy(synchronizedTransactionId = UUID.randomUUID())

      whenever(objectMapper.writeValueAsString(any())).thenReturn(differentBodyJson)
      whenever(jsonComparator.areJsonBodiesEqual(any(), any())).thenReturn(false)
      whenever(requestCaptureService.captureAndStoreRequest(any(), anyOrNull())).thenReturn(updatedPayload)

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.UPDATED)
      assertThat(result.synchronizedTransactionId).isEqualTo(updatedPayload.synchronizedTransactionId)
    }

    @Test
    fun `should default to empty JSON and update if serialization fails during comparison`() {
      whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
      whenever(syncQueryService.findByLegacyTransactionId(any())).thenReturn(dummyStoredPayload)
      whenever(objectMapper.writeValueAsString(any())).thenThrow(RuntimeException("Bad JSON"))
      whenever(jsonComparator.areJsonBodiesEqual(any(), eq("{}"))).thenReturn(false)

      whenever(requestCaptureService.captureAndStoreRequest(any(), anyOrNull())).thenReturn(dummyStoredPayload)

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.UPDATED)
    }
  }
}
