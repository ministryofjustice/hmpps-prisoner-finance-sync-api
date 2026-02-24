package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadCustomRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.AuditCursor
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.CursorPage
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadDetail
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.toDetail
import java.time.LocalDate
import java.util.UUID

@Service
class AuditHistoryService(
  private val nomisSyncPayloadCustomRepository: NomisSyncPayloadCustomRepository,
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
  ): CursorPage<NomisSyncPayload> {
    val cursor = AuditCursor.parse(cursorString)

    val normalizedPrisonId = prisonId?.takeIf { it.isNotBlank() && it != "null" }
    val startInstant = startDate?.let(timeConversionService::toUtcStartOfDay)
    val endInstant = endDate?.plusDays(1)?.let(timeConversionService::toUtcStartOfDay)

    val items = nomisSyncPayloadCustomRepository.findMatchingPayloads(
      prisonId = normalizedPrisonId,
      legacyTransactionId = legacyTransactionId,
      transactionType = transactionType,
      startDate = startInstant,
      endDate = endInstant,
      cursorTimestamp = cursor?.timestamp,
      cursorId = cursor?.id,
      pageable = PageRequest.of(0, size + 1, Sort.by(Sort.Direction.DESC, "timestamp", "id")),
    )

    val totalElements = nomisSyncPayloadCustomRepository.countMatchingPayloads(
      normalizedPrisonId,
      legacyTransactionId,
      transactionType,
      startInstant,
      endInstant,
    )

    return toCursorPage(items, totalElements, size)
  }

  fun getPayloadBodyByRequestId(requestId: UUID): NomisSyncPayloadDetail? = nomisSyncPayloadRepository.findByRequestId(requestId)?.toDetail()

  private fun toCursorPage(items: List<NomisSyncPayload>, total: Long, size: Int): CursorPage<NomisSyncPayload> {
    val hasNext = items.size > size
    val content = if (hasNext) items.take(size) else items
    val nextCursor = content.lastOrNull()?.takeIf { hasNext }?.let {
      AuditCursor(
        it.timestamp,
        /*TODO fix this typing*/
        it.id!!,
      ).toString()
    }

    return CursorPage(content, nextCursor, total, size)
  }
}
