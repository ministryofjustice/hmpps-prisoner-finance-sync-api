package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Transaction
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
interface TransactionRepository : JpaRepository<Transaction, Long> {
  fun findByUuid(uuid: UUID): Transaction?
  fun findByDateBetween(dateStart: Timestamp, dateEnd: Timestamp): List<Transaction>

  @Modifying
  @Query(
    """
      UPDATE Transaction t 
      SET t.auditStatus = "MERGED", t.modifiedAt = :mergedAt
      WHERE t.id = (SELECT te.id from TransactionEntry te where te.accountId = :toAccountId) """,
  )
  fun recordTransactionsMerged(toAccountId: Long, mergedAt: Instant)
}
