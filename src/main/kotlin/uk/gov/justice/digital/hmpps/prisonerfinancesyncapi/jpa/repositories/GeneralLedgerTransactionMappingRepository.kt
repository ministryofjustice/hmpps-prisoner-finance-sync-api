package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.GeneralLedgerTransactionMapping
import java.time.Instant
import java.util.UUID

@Repository
interface GeneralLedgerTransactionMappingRepository : JpaRepository<GeneralLedgerTransactionMapping, Long> {
  @Query(
    """
    SELECT glm FROM GeneralLedgerTransactionMapping glm
    WHERE glm.createdAt >= :dateStart AND glm.createdAt <:dateEnd
""",
  )
  fun findAllOnDate(dateStart: Instant, dateEnd: Instant): List<GeneralLedgerTransactionMapping>
  fun findGeneralLedgerTransactionMappingByGlTransactionUuid(glUUID: UUID): GeneralLedgerTransactionMapping?

  // fun findByGlTransactionUuid(glTransactionUUID: UUID): GeneralLedgerTransactionMapping?
}
