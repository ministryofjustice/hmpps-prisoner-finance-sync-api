package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Transaction
import java.sql.Timestamp
import java.util.UUID

@Repository
interface TransactionRepository : JpaRepository<Transaction, Long> {
  fun findByUuid(uuid: UUID): Transaction?
  fun findByDateBetween(dateStart: Timestamp, dateEnd: Timestamp): List<Transaction>
}
