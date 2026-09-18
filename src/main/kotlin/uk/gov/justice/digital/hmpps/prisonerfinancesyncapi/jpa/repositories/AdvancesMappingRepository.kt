package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AdvanceMapping

interface AdvancesMappingRepository : JpaRepository<AdvanceMapping, String> {
  fun findAdvanceMappingByLegacyPaymentProfileId(paymentProfileId: String): AdvanceMapping?
}
