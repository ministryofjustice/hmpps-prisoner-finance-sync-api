package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.TransactionSyncStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.JsonComparator
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.SyncQueryService
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SyncStatusResolverTest {

  @Mock
  private lateinit var syncQueryService: SyncQueryService

  @Mock
  private lateinit var jsonComparator: JsonComparator

  @Mock
  private lateinit var objectMapper: ObjectMapper

  @InjectMocks
  private lateinit var syncStatusResolver: SyncStatusResolver

  private lateinit var dummyRequest: SyncOffenderTransactionRequest
  private lateinit var dummyPayload: NomisSyncPayload
  private val syncId = UUID.randomUUID()

  @BeforeEach
  fun setup() {
    dummyRequest = SyncOffenderTransactionRequest(
      transactionId = 999L,
      requestId = UUID.randomUUID(),
      caseloadId = "MDI",
      offenderTransactions = emptyList(),
      transactionTimestamp = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      createdBy = "TEST_USER",
      createdByDisplayName = "Test User",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )

    dummyPayload = NomisSyncPayload(
      id = 1L,
      timestamp = Instant.now(),
      legacyTransactionId = 999L,
      requestId = dummyRequest.requestId,
      caseloadId = "MDI",
      requestTypeIdentifier = "SyncOffenderTransactionRequest",
      synchronizedTransactionId = syncId,
      body = "{\"offenderTransactions\":[]}",
      transactionType = "OT",
      transactionTimestamp = Instant.now(),
    )
  }

  @Test
  fun `should return Duplicate when requestId is already present in the sync logs`() {
    whenever(syncQueryService.findByRequestId(dummyRequest.requestId)).thenReturn(dummyPayload)

    val result = syncStatusResolver.check(dummyRequest)

    assertThat(result).isEqualTo(TransactionSyncStatus.Duplicate(syncId))
  }

  @Test
  fun `should return Duplicate when transactionId exists and the payload is identical`() {
    whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
    whenever(syncQueryService.findByLegacyTransactionId(dummyRequest.transactionId)).thenReturn(dummyPayload)
    whenever(objectMapper.writeValueAsString(any())).thenReturn("{\"offenderTransactions\":[]}")
    whenever(jsonComparator.areJsonBodiesEqual(any(), any())).thenReturn(true)

    val result = syncStatusResolver.check(dummyRequest)

    assertThat(result).isEqualTo(TransactionSyncStatus.Duplicate(syncId))
  }

  @Test
  fun `should return Updated when transactionId exists but the payload has changed`() {
    whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
    whenever(syncQueryService.findByLegacyTransactionId(dummyRequest.transactionId)).thenReturn(dummyPayload)
    whenever(objectMapper.writeValueAsString(any())).thenReturn("{\"offenderTransactions\":[{\"id\":1}]}")
    whenever(jsonComparator.areJsonBodiesEqual(any(), any())).thenReturn(false)

    val result = syncStatusResolver.check(dummyRequest)

    assertThat(result).isEqualTo(TransactionSyncStatus.Updated(syncId))
  }

  @Test
  fun `should return New when neither the requestId nor the transactionId are found`() {
    whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
    whenever(syncQueryService.findByLegacyTransactionId(any())).thenReturn(null)

    val result = syncStatusResolver.check(dummyRequest)

    assertThat(result).isEqualTo(TransactionSyncStatus.New)
  }

  @Test
  fun `should throw an exception when a serialization error occurs during the check`() {
    whenever(syncQueryService.findByRequestId(any())).thenReturn(null)
    whenever(syncQueryService.findByLegacyTransactionId(dummyRequest.transactionId)).thenReturn(dummyPayload)

    whenever(objectMapper.writeValueAsString(any())).thenThrow(RuntimeException("Serialization Error"))

    assertThrows(RuntimeException::class.java) {
      syncStatusResolver.check(dummyRequest)
    }
  }
}
