package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
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

  fun getPayloadsByCaseloadAndDateRange(prisonId: String?, startDate: LocalDate?, endDate: LocalDate?, page: Int, size: Int): Page<NomisSyncPayloadSummary> {
    val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"))

    val startOfStartDate = startDate?.let { timeConversionService.toUtcStartOfDay(startDate) }
    val endOfEndDate = endDate?.let { timeConversionService.toUtcStartOfDay(endDate.plusDays(1)) }

    return nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(prisonId, startOfStartDate, endOfEndDate, pageable)
  }

  fun getPayloadBodyByRequestId(requestId: UUID): NomisSyncPayloadDetail? = nomisSyncPayloadRepository.findByRequestId(requestId)?.toDetail()
}
