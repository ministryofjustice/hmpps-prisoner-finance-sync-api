package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.NomisSyncPayloadDto
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class AuditHistoryService(
  private val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
) {

  fun getPayloadsByCaseloadAndDateRange(prisonId: String?, startDate: Instant?, endDate: Instant?, page: Int, size: Int): Page<NomisSyncPayloadDto> {
    val pageable = PageRequest.of(page, size)

    var startDateReq = startDate
    var endDateReq = endDate

    if (startDateReq == null && endDateReq == null) {
      endDateReq = Instant.now()
      startDateReq = endDateReq.minus(30, ChronoUnit.DAYS)
    } else if (endDateReq == null) {
      endDateReq = Instant.now()
    } else if (startDateReq == null) {
      startDateReq = endDateReq.minus(30, ChronoUnit.DAYS)
    }

    val items = nomisSyncPayloadRepository.findByCaseloadIdAndDateRange(prisonId, startDateReq!!, endDateReq, pageable)

    return items
  }
}
