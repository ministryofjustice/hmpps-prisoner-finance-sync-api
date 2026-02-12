package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniqueCaseloadId
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.AuditCursor
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadSummary
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.toDetail
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
    override val id: Long,
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
    private val cursor = null
    private val size = 10
    private val pageable = PageRequest.of(0, size + 1, Sort.by(Sort.Direction.DESC, "timestamp", "id"))

    @Test
    fun `getMatchingPayloads should map entities to DTOs correctly`() {
      val caseloadId = "MDI"
      val requestId = UUID.randomUUID()
      val startDate = LocalDate.now().minus(30, ChronoUnit.DAYS)
      val endDate = LocalDate.now()

      val entity = TestNomisSyncPayloadSummary(
        id = 1L,
        timestamp = Instant.now(),
        legacyTransactionId = 12345L,
        synchronizedTransactionId = UUID.randomUUID(),
        requestId = requestId,
        caseloadId = caseloadId,
        requestTypeIdentifier = "TRANSFER",
        transactionTimestamp = Instant.now(),
      )

      whenever(
        nomisSyncPayloadRepository.findMatchingPayloads(
          eq(caseloadId),
          eq(null),
          anyOrNull(),
          anyOrNull(),
          eq(null),
          eq(null),
          eq(pageable),
        ),
      ).thenReturn(listOf(entity))

      whenever(nomisSyncPayloadRepository.countMatchingPayloads(eq(caseloadId), eq(null), anyOrNull(), anyOrNull()))
        .thenReturn(1L)

      val result = auditHistoryService.getMatchingPayloads(caseloadId, null, startDate, endDate, cursor, size)

      assertThat(result.content).hasSize(1)
      assertThat(result.content[0].requestId).isEqualTo(requestId)
    }

    @Test
    fun `Should pass BOTH dates as null to findMatchingPayloads`() {
      val endDateCaptor = argumentCaptor<Instant>()
      val startDateCaptor = argumentCaptor<Instant>()

      lenient().doReturn(emptyList<NomisSyncPayloadSummary>())
        .`when`(nomisSyncPayloadRepository).findMatchingPayloads(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
      lenient().doReturn(0L)
        .`when`(nomisSyncPayloadRepository).countMatchingPayloads(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())

      auditHistoryService.getMatchingPayloads("MDI", null, null, null, cursor, size)

      verify(nomisSyncPayloadRepository).findMatchingPayloads(
        eq("MDI"),
        eq(null),
        startDateCaptor.capture(),
        endDateCaptor.capture(),
        eq(null),
        eq(null),
        eq(pageable),
      )

      assertThat(startDateCaptor.firstValue).isNull()
      assertThat(endDateCaptor.firstValue).isNull()
    }

    @Test
    fun `Should pass legacyTransactionId to repository when provided`() {
      val legacyTxId = 12345L

      lenient().doReturn(emptyList<NomisSyncPayloadSummary>())
        .`when`(nomisSyncPayloadRepository).findMatchingPayloads(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
      lenient().doReturn(0L)
        .`when`(nomisSyncPayloadRepository).countMatchingPayloads(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())

      auditHistoryService.getMatchingPayloads("MDI", legacyTxId, null, null, cursor, size)

      verify(nomisSyncPayloadRepository).findMatchingPayloads(
        eq("MDI"),
        eq(legacyTxId),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq(pageable),
      )
    }

    @Test
    fun `should correctly handle hasNext and build nextCursor when more items exist`() {
      val now = Instant.now()
      val entities = (1..11).map { i ->
        TestNomisSyncPayloadSummary(
          id = i.toLong(),
          timestamp = now.minusSeconds(i.toLong()),
          legacyTransactionId = i.toLong(),
          synchronizedTransactionId = UUID.randomUUID(),
          requestId = UUID.randomUUID(),
          caseloadId = "MDI",
          requestTypeIdentifier = "SYNC",
          transactionTimestamp = now,
        )
      }

      whenever(nomisSyncPayloadRepository.findMatchingPayloads(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), eq(pageable)))
        .thenReturn(entities)
      whenever(nomisSyncPayloadRepository.countMatchingPayloads(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
        .thenReturn(100L)

      val result = auditHistoryService.getMatchingPayloads("MDI", null, null, null, null, 10)

      assertThat(result.content).hasSize(10)
      assertThat(result.nextCursor).isNotNull()

      val tenthItem = entities[9]
      val expectedCursor = AuditCursor(tenthItem.timestamp, tenthItem.id).toString()
      assertThat(result.nextCursor).isEqualTo(expectedCursor)
    }

    @Test
    fun `should pass decoded cursor components to repository`() {
      val cursorTimestamp = Instant.parse("2026-01-01T12:00:00Z")
      val cursorId = 999L
      val cursorString = AuditCursor(cursorTimestamp, cursorId).toString()

      whenever(nomisSyncPayloadRepository.findMatchingPayloads(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), eq(cursorTimestamp), eq(cursorId), eq(pageable)))
        .thenReturn(emptyList())
      whenever(nomisSyncPayloadRepository.countMatchingPayloads(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
        .thenReturn(0L)

      auditHistoryService.getMatchingPayloads(null, null, null, null, cursorString, 10)

      verify(nomisSyncPayloadRepository).findMatchingPayloads(
        prisonId = eq(null),
        legacyTransactionId = eq(null),
        startDate = eq(null),
        endDate = eq(null),
        cursorTimestamp = eq(cursorTimestamp),
        cursorId = eq(cursorId),
        pageable = eq(pageable),
      )
    }

    @Test
    fun `should normalize prisonId if blank or string null`() {
      whenever(nomisSyncPayloadRepository.findMatchingPayloads(eq(null), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
        .thenReturn(emptyList())
      whenever(nomisSyncPayloadRepository.countMatchingPayloads(eq(null), anyOrNull(), anyOrNull(), anyOrNull()))
        .thenReturn(0L)

      auditHistoryService.getMatchingPayloads(" ", null, null, null, null, 10)
      auditHistoryService.getMatchingPayloads("null", null, null, null, null, 10)

      verify(nomisSyncPayloadRepository, times(2)).findMatchingPayloads(
        prisonId = eq(null),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
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
        requestId = requestId,
        caseloadId = uniqueCaseloadId(),
        requestTypeIdentifier = "NewSyncType",
        synchronizedTransactionId = UUID.randomUUID(),
        body = """{"test": "data"}""",
        transactionTimestamp = Instant.now(),
      )
      whenever(nomisSyncPayloadRepository.findByRequestId(requestId)).thenReturn(payload)
      val result = auditHistoryService.getPayloadBodyByRequestId(requestId)
      assertThat(result).isEqualTo(payload.toDetail())
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
