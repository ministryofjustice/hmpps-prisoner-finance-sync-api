package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AdvanceRepaymentMapping

interface AdvanceRepaymentMappingRepository : JpaRepository<AdvanceRepaymentMapping, Long>
