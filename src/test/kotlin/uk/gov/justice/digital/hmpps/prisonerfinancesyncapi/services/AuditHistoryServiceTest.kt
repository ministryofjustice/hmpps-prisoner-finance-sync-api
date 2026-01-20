package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadSummary
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@DisplayName("Audit History Service Test")
class AuditHistoryServiceTest {

  @Mock
  private lateinit var nomisSyncPayloadRepository: NomisSyncPayloadRepository

  @InjectMocks
  private lateinit var auditHistoryService: AuditHistoryService

  data class TestNomisSyncPayloadSummary(
    override val legacyTransactionId: Long?,
    override val synchronizedTransactionId: UUID,
    override val caseloadId: String?,
    override val timestamp: Instant,
    override val requestTypeIdentifier: String?,
    override val requestId: UUID,
    override val transactionTimestamp: Instant?,
  ) : NomisSyncPayloadSummary

  @Nested
  inner class GetPayloadsByCaseloadTest {
    val page = 1
    val size = 10
    val pageable = PageRequest.of(page, size)

    @Test
    fun `getPayloadsByCaseload should map entities to DTOs correctly`() {
      val caseloadId = "MDI"
      val requestId = UUID.randomUUID()
      val syncTxId = UUID.randomUUID()
      val legacyTxId = 12345L

      val startDate = LocalDate.now().minus(30, ChronoUnit.DAYS)
      val endDate = LocalDate.now()

      val entity = TestNomisSyncPayloadSummary(
        timestamp = Instant.now(),
        legacyTransactionId = legacyTxId,
        synchronizedTransactionId = syncTxId,
        requestId = requestId,
        caseloadId = caseloadId,
        requestTypeIdentifier = "TRANSFER",
        transactionTimestamp = Instant.parse("2026-01-19T10:00:00Z"),
      )

      whenever(
        nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(
          caseloadId,
          startDate,
          endDate,
          pageable,
        ),
      )
        .thenReturn(PageImpl(listOf(entity)))

      val result = auditHistoryService.getPayloadsByCaseloadAndDateRange(caseloadId, startDate, endDate, page, size)

      assertThat(result).hasSize(1)
      with(result.content[0]) {
        assertThat(this.caseloadId).isEqualTo(caseloadId)
        assertThat(this.requestId).isEqualTo(requestId)
        assertThat(this.synchronizedTransactionId).isEqualTo(syncTxId)
        assertThat(this.legacyTransactionId).isEqualTo(legacyTxId)
      }
    }

    @Test
    fun `getPayloadsByCaseload should return empty list when no records found`() {
      val caseloadId = "EMPTY"

      val startDate = LocalDate.now().minus(30, ChronoUnit.DAYS)
      val endDate = LocalDate.now()

      whenever(
        nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(
          caseloadId,
          startDate,
          endDate,
          pageable,
        ),
      ).thenReturn(PageImpl(emptyList()))

      val result = auditHistoryService.getPayloadsByCaseloadAndDateRange(caseloadId, startDate, endDate, page, size)

      assertThat(result).isEmpty()
    }

    @Test
    fun `Should default to 30 days ago to now when BOTH dates are null`() {
      val startDateCaptor = argumentCaptor<LocalDate>()
      val endDateCaptor = argumentCaptor<LocalDate>()

      whenever(nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(any(), any(), any(), any()))
        .thenReturn(PageImpl(emptyList()))

      auditHistoryService.getPayloadsByCaseloadAndDateRange("MDI", null, null, 0, 10)

      verify(nomisSyncPayloadRepository).findByCaseloadIdAndDateRange(
        eq("MDI"),
        startDateCaptor.capture(),
        endDateCaptor.capture(),
        eq(PageRequest.of(0, 10)),
      )

      val start = startDateCaptor.firstValue
      val end = endDateCaptor.firstValue

      assertThat(end).isBeforeOrEqualTo(LocalDate.now())
      assertThat(start).isEqualTo(end.minus(30, ChronoUnit.DAYS))
    }

    @Test
    fun `Should defaults endDate to now when ONLY endDate is null`() {
      val fixedStart = LocalDate.parse("2026-01-01")
      val endDateCaptor = argumentCaptor<LocalDate>()

      whenever(nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(any(), any(), any(), any()))
        .thenReturn(PageImpl(emptyList()))

      auditHistoryService.getPayloadsByCaseloadAndDateRange("MDI", fixedStart, null, 0, 10)

      verify(nomisSyncPayloadRepository).findByCaseloadIdAndDateRange(
        eq("MDI"),
        eq(fixedStart),
        endDateCaptor.capture(),
        any(),
      )

      assertThat(endDateCaptor.firstValue).isBeforeOrEqualTo(LocalDate.now())
    }

    @Test
    fun `Should defaults startDate to 1 day before endDate when ONLY startDate is null `() {
      val fixedEnd = LocalDate.parse("2026-02-01")
      val startDateCaptor = argumentCaptor<LocalDate>()

      whenever(nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(any(), any(), any(), any()))
        .thenReturn(PageImpl(emptyList()))

      auditHistoryService.getPayloadsByCaseloadAndDateRange("MDI", null, fixedEnd, 0, 10)

      verify(nomisSyncPayloadRepository).findByCaseloadIdAndDateRange(
        eq("MDI"),
        startDateCaptor.capture(),
        eq(fixedEnd),
        any(),
      )

      assertThat(startDateCaptor.firstValue).isEqualTo(fixedEnd.minus(30, ChronoUnit.DAYS))
    }

    @Test
    fun `Should use provided dates without modification when BOTH dates provided`() {
      val start = LocalDate.parse("2026-01-01")
      val end = LocalDate.parse("2026-01-05")

      whenever(nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(any(), any(), any(), any()))
        .thenReturn(PageImpl(emptyList()))

      auditHistoryService.getPayloadsByCaseloadAndDateRange("MDI", start, end, 0, 10)

      verify(nomisSyncPayloadRepository).findByCaseloadIdAndDateRange(
        eq("MDI"),
        eq(start),
        eq(end),
        any(),
      )
    }
  }
}
