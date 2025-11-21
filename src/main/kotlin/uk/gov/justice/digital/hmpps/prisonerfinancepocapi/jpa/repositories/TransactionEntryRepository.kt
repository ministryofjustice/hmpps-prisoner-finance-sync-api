package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities.TransactionEntry
import java.math.BigDecimal
import java.sql.Timestamp

@Repository
interface TransactionEntryRepository : JpaRepository<TransactionEntry, Long> {
  fun findByTransactionId(transactionId: Long): List<TransactionEntry>
  fun findByAccountId(accountId: Long): List<TransactionEntry>

  @Query(
    """
    SELECT te FROM TransactionEntry te 
    JOIN Transaction t ON te.transactionId = t.id 
    WHERE t.date BETWEEN :startDate AND :endDate 
    AND t.prison = :prisonCode
""",
  )
  fun findByDateBetweenAndPrisonCode(startDate: Timestamp, endDate: Timestamp, prisonCode: String): List<TransactionEntry>

  @Query(
    """
    SELECT SUM(
        CASE 
            WHEN te.entryType = a.postingType THEN te.amount 
            ELSE 0 - te.amount 
        END
    )
    FROM TransactionEntry te 
    JOIN Account a ON te.accountId = a.id
    JOIN Transaction t ON te.transactionId = t.id
    WHERE a.accountCode = :targetAccountCode
      AND t.prison = :prisonCode
      AND t.transactionType NOT IN :migrationTypes
      AND t.date >= :cutoffDate
""",
  )
  fun calculateNetBalanceAdjustment(
    @Param("targetAccountCode") targetAccountCode: Int,
    @Param("prisonCode") prisonCode: String,
    @Param("cutoffDate") cutoffDate: Timestamp,
    @Param("migrationTypes") migrationTypes: Set<String>,
  ): BigDecimal?
}
