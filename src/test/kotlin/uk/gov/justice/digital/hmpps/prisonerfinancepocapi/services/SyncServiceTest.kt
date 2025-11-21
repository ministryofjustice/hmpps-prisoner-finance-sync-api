package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.services.ledger.LedgerSyncService
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
        GeneralLedgerEntry(entrySequence = 1, code = 1101, postingType = "DR", amount = 50.00),
        GeneralLedgerEntry(entrySequence = 2, code = 2503, postingType = "CR", amount = 50.00),
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
      `when`(syncQueryService.findByRequestId(any())).thenReturn(dummyStoredPayload)

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
      `when`(syncQueryService.findByRequestId(any())).thenReturn(null)
      `when`(syncQueryService.findByLegacyTransactionId(any())).thenReturn(null)
      `when`(requestCaptureService.captureAndStoreRequest(any(), anyOrNull())).thenReturn(dummyStoredPayload)
      `when`(ledgerSyncService.syncGeneralLedgerTransaction(any())).thenReturn(dummyStoredPayload.synchronizedTransactionId)

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.CREATED)
      assertThat(result.requestId).isEqualTo(dummyGeneralLedgerTransactionRequest.requestId)
      assertThat(result.synchronizedTransactionId).isEqualTo(dummyStoredPayload.synchronizedTransactionId)
      verify(syncQueryService, times(1)).findByRequestId(any())
      verify(syncQueryService, times(1)).findByLegacyTransactionId(any())
      verify(ledgerSyncService, times(1)).syncGeneralLedgerTransaction(any())
      verify(requestCaptureService, times(1)).captureAndStoreRequest(any(), eq(dummyStoredPayload.synchronizedTransactionId))
    }

    @Nested
    @DisplayName("when a transactionId exists but requestId does not")
    inner class TransactionIdExistsTests {

      @BeforeEach
      fun setup() {
        `when`(syncQueryService.findByRequestId(any())).thenReturn(null)
        `when`(syncQueryService.findByLegacyTransactionId(any())).thenReturn(dummyStoredPayload)
      }

      @Test
      fun `should return PROCESSED if the body JSON is identical`() {
        val newBodyJson = "{\"transactionId\":19228029,\"requestId\":\"c3d4e5f6-a7b8-9012-3456-7890abcdef01\"}"
        `when`(objectMapper.writeValueAsString(any())).thenReturn(newBodyJson)
        `when`(jsonComparator.areJsonBodiesEqual(any(), any())).thenReturn(true)

        val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

        assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.PROCESSED)
        assertThat(result.requestId).isEqualTo(dummyGeneralLedgerTransactionRequest.requestId)
        assertThat(result.synchronizedTransactionId).isEqualTo(dummyStoredPayload.synchronizedTransactionId)
        verify(objectMapper, times(1)).writeValueAsString(any())
        verify(jsonComparator, times(1)).areJsonBodiesEqual(any(), any())
        verify(ledgerSyncService, times(0)).syncGeneralLedgerTransaction(any())
        verify(requestCaptureService, times(0)).captureAndStoreRequest(any(), anyOrNull())
      }

      @Test
      fun `should return UPDATED if the body JSON is different`() {
        val differentBodyJson = "{\"transactionId\":19228029,\"requestId\":\"c3d4e5f6-a7b8-9012-3456-7890abcdef01\",\"newField\":\"value\"}"
        val updatedPayload = dummyStoredPayload.copy(synchronizedTransactionId = UUID.randomUUID())
        `when`(objectMapper.writeValueAsString(any())).thenReturn(differentBodyJson)
        `when`(jsonComparator.areJsonBodiesEqual(any(), any())).thenReturn(false)
        `when`(requestCaptureService.captureAndStoreRequest(any(), anyOrNull())).thenReturn(updatedPayload)

        val expectedSyncId = dummyStoredPayload.synchronizedTransactionId

        val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

        assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.UPDATED)
        assertThat(result.requestId).isEqualTo(dummyGeneralLedgerTransactionRequest.requestId)
        assertThat(result.synchronizedTransactionId).isEqualTo(updatedPayload.synchronizedTransactionId)
        verify(objectMapper, times(1)).writeValueAsString(any())
        verify(jsonComparator, times(1)).areJsonBodiesEqual(any(), any())
        verify(ledgerSyncService, times(0)).syncGeneralLedgerTransaction(any())
        verify(requestCaptureService, times(1)).captureAndStoreRequest(any(), eq(expectedSyncId))
      }
    }
  }
}
