package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayloadDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadSummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AuditHistoryService(
  private val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
  private val timeConversionService: TimeConversionService,
) {

  fun getPayloadsByCaseloadAndDateRange(prisonId: String?, startDate: LocalDate?, endDate: LocalDate?, page: Int, size: Int): Page<NomisSyncPayloadSummary> {
    val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"))

    val endDateReq = endDate ?: LocalDate.now()
    val startDateReq = startDate ?: endDateReq.minus(30, ChronoUnit.DAYS)

    val startOfStartDate = timeConversionService.toUtcStartOfDay(startDateReq)
    val endOfEndDate = timeConversionService.toUtcStartOfDay(endDateReq.plusDays(1))

    val items = nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(prisonId, startOfStartDate, endOfEndDate, pageable)

    return items
  }

  fun getPayloadBodyByRequestId(transactionId: UUID): NomisSyncPayloadDetails? {
    val payload = nomisSyncPayloadRepository.findByRequestId(transactionId)

    return payload
  }
}
