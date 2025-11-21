package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities.Prison

@Repository
interface PrisonRepository : JpaRepository<Prison, Long> {
  fun findByCode(code: String): Prison?
}
