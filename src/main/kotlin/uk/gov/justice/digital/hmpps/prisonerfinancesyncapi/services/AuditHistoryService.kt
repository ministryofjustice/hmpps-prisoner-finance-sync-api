package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.NomisSyncPayloadDto
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.toDto

@Service
class AuditHistoryService(
  private val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
) {

  fun getPayloadsByCaseload(caseloadId: String): List<NomisSyncPayloadDto> {
    val items = nomisSyncPayloadRepository.findByCaseloadId(caseloadId)
      .map { it.toDto() }

    return items
  }
}
