package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@DisplayName("Audit History Service Test")
class AuditHistoryServiceTest {

  @Mock
  private lateinit var nomisSyncPayloadRepository: NomisSyncPayloadRepository

  @InjectMocks
  private lateinit var auditHistoryService: AuditHistoryService

  @Nested
  inner class GetPayloadsByCaseloadTest {
    @Test
    fun `getPayloadsByCaseload should map entities to DTOs correctly`() {
      val caseloadId = "MDI"
      val requestId = UUID.randomUUID()
      val syncTxId = UUID.randomUUID()
      val legacyTxId = 12345L
      val entity = NomisSyncPayload(
        id = 100L,
        timestamp = Instant.now(),
        legacyTransactionId = legacyTxId,
        synchronizedTransactionId = syncTxId,
        requestId = requestId,
        caseloadId = caseloadId,
        requestTypeIdentifier = "TRANSFER",
        transactionTimestamp = Instant.parse("2026-01-19T10:00:00Z"),
        body = """{"amount": 10.50}""",
      )

      whenever(nomisSyncPayloadRepository.findByCaseloadId(caseloadId))
        .thenReturn(listOf(entity))

      val result = auditHistoryService.getPayloadsByCaseload(caseloadId)

      assertThat(result).hasSize(1)
      with(result[0]) {
        assertThat(this.caseloadId).isEqualTo(caseloadId)
        assertThat(this.requestId).isEqualTo(requestId)
        assertThat(this.synchronizedTransactionId).isEqualTo(syncTxId)
        assertThat(this.legacyTransactionId).isEqualTo(legacyTxId)
      }
    }

    @Test
    fun `getPayloadsByCaseload should return empty list when no records found`() {
      val caseloadId = "EMPTY"
      whenever(nomisSyncPayloadRepository.findByCaseloadId(caseloadId)).thenReturn(emptyList())

      val result = auditHistoryService.getPayloadsByCaseload(caseloadId)

      assertThat(result).isEmpty()
    }
  }
}
