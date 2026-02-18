package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.AuditCursor
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.CursorPage
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadDetail
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadSummary
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.toDetail
import java.time.LocalDate
import java.util.UUID

@Service
class AuditHistoryService(
  private val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
  private val timeConversionService: TimeConversionService,
) {

  fun getMatchingPayloads(
    prisonId: String?,
    legacyTransactionId: Long?,
    transactionType: String?,
    startDate: LocalDate?,
    endDate: LocalDate?,
    cursorString: String?,
    size: Int,
  ): CursorPage<NomisSyncPayloadSummary> {
    val cursor = AuditCursor.parse(cursorString)

    val normalizedPrisonId = prisonId?.takeIf { it.isNotBlank() && it != "null" }
    val startInstant = startDate?.let(timeConversionService::toUtcStartOfDay)
    val endInstant = endDate?.plusDays(1)?.let(timeConversionService::toUtcStartOfDay)

    val items = nomisSyncPayloadRepository.findMatchingPayloads(
      prisonId = normalizedPrisonId,
      legacyTransactionId = legacyTransactionId,
      transactionType = transactionType,
      startDate = startInstant,
      endDate = endInstant,
      cursorTimestamp = cursor?.timestamp,
      cursorId = cursor?.id,
      pageable = PageRequest.of(0, size + 1, Sort.by(Sort.Direction.DESC, "timestamp", "id")),
    )

    val totalElements = nomisSyncPayloadRepository.countMatchingPayloads(
      normalizedPrisonId,
      legacyTransactionId,
      transactionType,
      startInstant,
      endInstant,
    )

    return toCursorPage(items, totalElements, size)
  }

  fun getPayloadBodyByRequestId(requestId: UUID): NomisSyncPayloadDetail? = nomisSyncPayloadRepository.findByRequestId(requestId)?.toDetail()

  private fun toCursorPage(items: List<NomisSyncPayloadSummary>, total: Long, size: Int): CursorPage<NomisSyncPayloadSummary> {
    val hasNext = items.size > size
    val content = if (hasNext) items.take(size) else items
    val nextCursor = content.lastOrNull()?.takeIf { hasNext }?.let { AuditCursor(it.timestamp, it.id).toString() }

    return CursorPage(content, nextCursor, total, size)
  }
}
