package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniqueCaseloadId
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
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

  @Spy
  private var timeConversionService = TimeConversionService()

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
    val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"))

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
          timeConversionService.toUtcStartOfDay(startDate),
          timeConversionService.toUtcStartOfDay(endDate.plusDays(1)),
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
          timeConversionService.toUtcStartOfDay(startDate),
          timeConversionService.toUtcStartOfDay(endDate.plusDays(1)),
          pageable,
        ),
      ).thenReturn(PageImpl(emptyList()))

      val result = auditHistoryService.getPayloadsByCaseloadAndDateRange(caseloadId, startDate, endDate, page, size)

      assertThat(result).isEmpty()
    }

    @Test
    fun `Should default to 30 days ago to now when BOTH dates are null`() {
      val startDateCaptor = argumentCaptor<Instant>()
      val endDateCaptor = argumentCaptor<Instant>()

      whenever(nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(any(), any(), any(), any()))
        .thenReturn(PageImpl(emptyList()))

      auditHistoryService.getPayloadsByCaseloadAndDateRange("MDI", null, null, page, size)

      verify(nomisSyncPayloadRepository).findByCaseloadIdAndDateRange(
        eq("MDI"),
        startDateCaptor.capture(),
        endDateCaptor.capture(),
        eq(pageable),
      )

      val expectedEnd = timeConversionService.toUtcStartOfDay(LocalDate.now().plusDays(1))
      val expectedStart = timeConversionService.toUtcStartOfDay(LocalDate.now().minusDays(30))

      assertThat(endDateCaptor.firstValue).isEqualTo(expectedEnd)
      assertThat(startDateCaptor.firstValue).isEqualTo(expectedStart)
    }

    @Test
    fun `Should defaults endDate to now when ONLY endDate is null`() {
      val fixedStart = LocalDate.parse("2026-01-01")
      val endDateCaptor = argumentCaptor<Instant>()

      whenever(nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(any(), any(), any(), any()))
        .thenReturn(PageImpl(emptyList()))

      auditHistoryService.getPayloadsByCaseloadAndDateRange("MDI", fixedStart, null, page, size)

      val expectedStart = timeConversionService.toUtcStartOfDay(fixedStart)
      val expectedEnd = timeConversionService.toUtcStartOfDay(LocalDate.now().plusDays(1))
      verify(nomisSyncPayloadRepository).findByCaseloadIdAndDateRange(
        eq("MDI"),
        eq(expectedStart),
        endDateCaptor.capture(),
        eq(pageable),
      )

      assertThat(endDateCaptor.firstValue).isEqualTo(expectedEnd)
    }

    @Test
    fun `Should defaults startDate to 30 days before endDate when ONLY startDate is null`() {
      val fixedEnd = LocalDate.parse("2026-02-01")
      val startDateCaptor = argumentCaptor<Instant>()

      whenever(nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(any(), any(), any(), any()))
        .thenReturn(PageImpl(emptyList()))

      auditHistoryService.getPayloadsByCaseloadAndDateRange("MDI", null, fixedEnd, page, size)

      val expectedEnd = timeConversionService.toUtcStartOfDay(fixedEnd.plusDays(1))
      verify(nomisSyncPayloadRepository).findByCaseloadIdAndDateRange(
        eq("MDI"),
        startDateCaptor.capture(),
        eq(expectedEnd),
        eq(pageable),
      )

      assertThat(startDateCaptor.firstValue).isEqualTo(timeConversionService.toUtcStartOfDay(fixedEnd.minus(30, ChronoUnit.DAYS)))
    }

    @Test
    fun `Should use provided dates without modification when BOTH dates provided`() {
      val start = LocalDate.parse("2026-01-01")
      val end = LocalDate.parse("2026-01-05")

      whenever(nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(any(), any(), any(), any()))
        .thenReturn(PageImpl(emptyList()))

      auditHistoryService.getPayloadsByCaseloadAndDateRange("MDI", start, end, page, size)

      val expectedStart = timeConversionService.toUtcStartOfDay(start)
      val expectedEnd = timeConversionService.toUtcStartOfDay(end.plusDays(1))
      verify(nomisSyncPayloadRepository).findByCaseloadIdAndDateRange(
        eq("MDI"),
        eq(expectedStart),
        eq(expectedEnd),
        eq(pageable),
      )
    }
  }

  @Nested
  inner class GetPayloadBodyByRequestId {
    @Test
    fun `returns payload body when payload exists`() {
      val requestId = UUID.randomUUID()
      val payload = NomisSyncPayload(
        timestamp = Instant.now(),
        legacyTransactionId = 1003L,
        requestId = UUID.randomUUID(),
        caseloadId = uniqueCaseloadId(),
        requestTypeIdentifier = "NewSyncType",
        synchronizedTransactionId = UUID.randomUUID(),
        body = """{"test": "data"}""",
        transactionTimestamp = Instant.now(),
      )

      whenever(
        nomisSyncPayloadRepository.findByRequestId(requestId),
      ).thenReturn(payload)

      val result = auditHistoryService.getPayloadBodyByRequestId(requestId)

      assertThat(result).isEqualTo(payload)
    }

    @Test
    fun `returns null when payload does not exist`() {
      val requestId = UUID.randomUUID()

      doReturn(null)
        .whenever(nomisSyncPayloadRepository)
        .findByRequestId(requestId)

      val result = auditHistoryService.getPayloadBodyByRequestId(requestId)

      assertThat(result).isNull()
    }
  }
}
