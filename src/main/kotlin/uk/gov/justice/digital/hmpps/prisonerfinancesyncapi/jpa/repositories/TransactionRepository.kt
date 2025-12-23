package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Transaction
import java.sql.Timestamp
import java.util.UUID

@Repository
interface TransactionRepository : JpaRepository<Transaction, Long> {
  @Query(
    """
        SELECT DISTINCT t 
        FROM Transaction t 
        INNER JOIN TransactionEntry te ON t.id = te.transactionId 
        WHERE te.accountId = :accountId
    """,
  )
  fun findAllByAccountId(@Param("accountId") accountId: Long): List<Transaction>
  fun findByUuid(uuid: UUID): Transaction?
  fun findByDateBetween(dateStart: Timestamp, dateEnd: Timestamp): List<Transaction>
}
