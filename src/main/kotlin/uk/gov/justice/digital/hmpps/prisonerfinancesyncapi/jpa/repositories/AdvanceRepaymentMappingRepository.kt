package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AdvanceRepaymentMapping
import java.util.UUID

interface AdvanceRepaymentMappingRepository : JpaRepository<AdvanceRepaymentMapping, Long> {
  fun getAdvanceRepaymentMappingByGeneralLedgerTransactionUuid(glUUID: UUID): AdvanceRepaymentMapping?
}
