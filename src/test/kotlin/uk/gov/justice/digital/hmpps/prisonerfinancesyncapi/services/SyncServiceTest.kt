package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.TransactionSyncStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerSyncService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync.SyncPayloadCaptureService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync.SyncStatusResolver
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SyncServiceTest {

  @Mock
  private lateinit var ledgerSyncService: LedgerSyncService

  @Mock
  private lateinit var syncPayloadCaptureService: SyncPayloadCaptureService

  @Mock
  private lateinit var syncStatusResolver: SyncStatusResolver

  @Mock
  private lateinit var telemetryClient: TelemetryClient

  @InjectMocks
  private lateinit var syncService: SyncService

  @Captor
  private lateinit var telemetryPropertiesCaptor: ArgumentCaptor<Map<String, String>>

  private lateinit var dummyGeneralLedgerTransactionRequest: SyncGeneralLedgerTransactionRequest
  private lateinit var dummyOffenderTransactionRequest: SyncOffenderTransactionRequest
  private lateinit var dummyStoredPayload: NomisSyncPayload
  private val syncId = UUID.fromString("a1a1a1a1-b1b1-c1c1-d1d1-e1e1e1e1e1e1")

  @BeforeEach
  fun setupGlobalDummies() {
    val now = LocalDateTime.now()

    dummyGeneralLedgerTransactionRequest = SyncGeneralLedgerTransactionRequest(
      transactionId = 19228029,
      requestId = UUID.fromString("c3d4e5f6-a7b8-9012-3456-7890abcdef01"),
      description = "General Ledger Account Transfer",
      reference = "REF12345",
      caseloadId = "MDI",
      transactionType = "GJ",
      transactionTimestamp = now,
      createdAt = now,
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

    dummyOffenderTransactionRequest = SyncOffenderTransactionRequest(
      transactionId = 999L,
      requestId = UUID.randomUUID(),
      caseloadId = "MDI",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 12345L,
          offenderDisplayId = "A1234AA",
          offenderBookingId = 54321L,
          subAccountType = "REG",
          postingType = "CR",
          type = "TIR",
          description = "Transfer In Regular from LEI",
          amount = BigDecimal("10.00"),
          reference = null,
          generalLedgerEntries = emptyList(),
        ),
      ),
      transactionTimestamp = now,
      createdAt = now,
      createdBy = "JD12346",
      createdByDisplayName = "J. Doe",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    dummyStoredPayload = NomisSyncPayload(
      id = 1L,
      timestamp = Instant.now(),
      legacyTransactionId = dummyGeneralLedgerTransactionRequest.transactionId,
      requestId = dummyGeneralLedgerTransactionRequest.requestId,
      caseloadId = dummyGeneralLedgerTransactionRequest.caseloadId,
      requestTypeIdentifier = SyncGeneralLedgerTransactionRequest::class.simpleName,
      synchronizedTransactionId = syncId,
      body = "{}",
      transactionType = "TEST",
      transactionTimestamp = Instant.now(),
    )
  }

  @Nested
  @DisplayName("syncTransaction")
  inner class SyncTransactionTests {

    @Test
    fun `should return PROCESSED if a request with the same requestId already exists`() {
      whenever(syncStatusResolver.check(any())).thenReturn(TransactionSyncStatus.Duplicate(syncId))

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.PROCESSED)
      assertThat(result.requestId).isEqualTo(dummyGeneralLedgerTransactionRequest.requestId)
      assertThat(result.synchronizedTransactionId).isEqualTo(syncId)
      verify(ledgerSyncService, times(0)).syncGeneralLedgerTransaction(any())
    }

    @Test
    fun `should return CREATED if neither requestId nor transactionId exists`() {
      whenever(syncStatusResolver.check(any())).thenReturn(TransactionSyncStatus.New)
      whenever(ledgerSyncService.syncGeneralLedgerTransaction(any())).thenReturn(syncId)
      whenever(syncPayloadCaptureService.captureAndStoreRequest(any(), eq(syncId))).thenReturn(dummyStoredPayload)

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.CREATED)
      assertThat(result.requestId).isEqualTo(dummyGeneralLedgerTransactionRequest.requestId)
      assertThat(result.synchronizedTransactionId).isEqualTo(syncId)
      verify(ledgerSyncService, times(1)).syncGeneralLedgerTransaction(any())
    }

    @Test
    fun `should retry and succeed when DataIntegrityViolationException occurs`() {
      whenever(syncStatusResolver.check(any())).thenReturn(TransactionSyncStatus.New)
      whenever(ledgerSyncService.syncGeneralLedgerTransaction(any()))
        .thenThrow(DataIntegrityViolationException("Race condition"))
        .thenReturn(syncId)
      whenever(syncPayloadCaptureService.captureAndStoreRequest(any(), any())).thenReturn(dummyStoredPayload)

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.CREATED)
      verify(ledgerSyncService, times(2)).syncGeneralLedgerTransaction(any())
    }

    @Test
    fun `should fail and send specific GL properties to App Insights if retry also fails`() {
      whenever(syncStatusResolver.check(any())).thenReturn(TransactionSyncStatus.New)
      whenever(ledgerSyncService.syncGeneralLedgerTransaction(any()))
        .thenThrow(DataIntegrityViolationException("Race condition"))
        .thenThrow(RuntimeException("Retry failed"))

      assertThrows(RuntimeException::class.java) {
        syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)
      }

      verify(telemetryClient, times(1)).trackException(any(), telemetryPropertiesCaptor.capture(), isNull())

      val capturedProperties = telemetryPropertiesCaptor.value
      assertThat(capturedProperties).containsEntry("requestId", dummyGeneralLedgerTransactionRequest.requestId.toString())
      assertThat(capturedProperties).containsEntry("transactionId", dummyGeneralLedgerTransactionRequest.transactionId.toString())
      assertThat(capturedProperties).containsEntry("requestType", "SyncGeneralLedgerTransactionRequest")
      assertThat(capturedProperties).containsEntry("transactionType", "GJ")
    }

    @Test
    fun `should fail and send specific Offender properties to App Insights on standard exception`() {
      whenever(syncStatusResolver.check(any())).thenReturn(TransactionSyncStatus.New)
      whenever(ledgerSyncService.syncOffenderTransaction(any()))
        .thenThrow(RuntimeException("Boom!"))

      assertThrows(RuntimeException::class.java) {
        syncService.syncTransaction(dummyOffenderTransactionRequest)
      }

      verify(telemetryClient, times(1)).trackException(any(), telemetryPropertiesCaptor.capture(), isNull())

      val capturedProperties = telemetryPropertiesCaptor.value
      assertThat(capturedProperties).containsEntry("requestId", dummyOffenderTransactionRequest.requestId.toString())
      assertThat(capturedProperties).containsEntry("transactionId", dummyOffenderTransactionRequest.transactionId.toString())
      assertThat(capturedProperties).containsEntry("requestType", "SyncOffenderTransactionRequest")
      assertThat(capturedProperties).containsEntry("transactionType", "TIR")
    }

    @Test
    fun `should throw IllegalArgumentException for unknown request types`() {
      whenever(syncStatusResolver.check(any())).thenReturn(TransactionSyncStatus.New)
      val unknownRequest = Mockito.mock(uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncRequest::class.java)
      whenever(unknownRequest.requestId).thenReturn(UUID.randomUUID())
      whenever(unknownRequest.transactionId).thenReturn(1L)

      assertThrows(IllegalArgumentException::class.java) {
        syncService.syncTransaction(unknownRequest)
      }
    }

    @Test
    fun `should return PROCESSED if the body JSON is identical to existing transaction`() {
      whenever(syncStatusResolver.check(any())).thenReturn(TransactionSyncStatus.Duplicate(syncId))

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.PROCESSED)
      assertThat(result.synchronizedTransactionId).isEqualTo(syncId)
    }

    @Test
    fun `should return UPDATED if the body JSON is different to existing transaction`() {
      whenever(syncStatusResolver.check(any())).thenReturn(TransactionSyncStatus.Updated(syncId))
      whenever(syncPayloadCaptureService.captureAndStoreRequest(any(), eq(syncId))).thenReturn(dummyStoredPayload)

      val result = syncService.syncTransaction(dummyGeneralLedgerTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.UPDATED)
      verify(syncPayloadCaptureService).captureAndStoreRequest(any(), eq(syncId))
    }

    @Test
    fun `should use the first synchronized transaction ID for offender transaction syncs`() {
      val transactionUuid1 = UUID.randomUUID()
      whenever(syncStatusResolver.check(any())).thenReturn(TransactionSyncStatus.New)
      whenever(ledgerSyncService.syncOffenderTransaction(any())).thenReturn(listOf(transactionUuid1))

      val storedPayload = dummyStoredPayload.copy(synchronizedTransactionId = transactionUuid1)
      whenever(syncPayloadCaptureService.captureAndStoreRequest(any(), eq(transactionUuid1))).thenReturn(storedPayload)

      val result = syncService.syncTransaction(dummyOffenderTransactionRequest)

      assertThat(result.action).isEqualTo(SyncTransactionReceipt.Action.CREATED)
      verify(syncPayloadCaptureService).captureAndStoreRequest(any(), eq(transactionUuid1))
      assertThat(result.synchronizedTransactionId).isEqualTo(transactionUuid1)
    }
  }
}
