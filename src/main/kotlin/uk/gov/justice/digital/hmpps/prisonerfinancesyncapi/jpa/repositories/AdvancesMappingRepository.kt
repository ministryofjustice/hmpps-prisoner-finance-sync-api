package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AdvanceMapping

interface AdvancesMappingRepository : JpaRepository<AdvanceMapping, Long> {
  fun findAdvanceMappingByLegacyPaymentProfileId(paymentProfileId: Long): AdvanceMapping?
}
