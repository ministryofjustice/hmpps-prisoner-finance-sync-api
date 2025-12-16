package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Transaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.TransactionEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionEntryRepository
import java.time.Instant

@Service
class MigrationFilterService(
  private val transactionEntryRepository: TransactionEntryRepository,
) {
  val migrationTypes = setOf("OB", "OHB")

  data class LatestMigrationInfo(
    val createdAt: Instant,
    val transactionDate: Instant,
  )

  /**
   * Returns transaction entries that occurred on or after the latest migration cutoff point
   * for a whole account (ignoring prison code).
   */
  fun getPostMigrationTransactionEntries(accountId: Long, allEntries: List<TransactionEntry>, allTransactions: Map<Long, Transaction>): List<TransactionEntry> {
    val latestMigrationInfo = findLatestMigrationInfo(accountId, allTransactions)
    return filterEntries(allEntries, allTransactions, latestMigrationInfo)
  }

  /**
   * Returns transaction entries that occurred on or after the latest migration cutoff point
   * for a specific prison within an account.
   */
  fun getPostMigrationTransactionEntriesForPrison(accountId: Long, prisonCode: String, allEntries: List<TransactionEntry>, allTransactions: Map<Long, Transaction>): List<TransactionEntry> {
    val latestMigrationInfo = findLatestMigrationInfoForPrison(accountId, prisonCode, allTransactions)
    return filterEntries(allEntries, allTransactions, latestMigrationInfo)
  }

  private fun filterEntries(
    entriesToFilter: List<TransactionEntry>,
    allTransactions: Map<Long, Transaction>,
    latestMigrationInfo: LatestMigrationInfo?,
  ): List<TransactionEntry> {
    if (latestMigrationInfo == null) {
      return entriesToFilter
    }

    return entriesToFilter.filter { entry ->
      val transaction = allTransactions[entry.transactionId] ?: return@filter false
      val transactionInstant = transaction.date.toInstant()

      val isLatestMigrationTransaction = transaction.createdAt?.equals(latestMigrationInfo.createdAt) ?: false
      val isPostMigrationActivity = transactionInstant.isAfter(latestMigrationInfo.transactionDate)

      isLatestMigrationTransaction || isPostMigrationActivity
    }
  }

  fun findLatestMigrationInfo(accountId: Long, allTransactions: Map<Long, Transaction>): LatestMigrationInfo? {
    val transactionIds = transactionEntryRepository.findByAccountId(accountId).map { it.transactionId }.distinct()

    val latestTransaction = allTransactions.values
      .filter { it.id in transactionIds && it.transactionType in migrationTypes }
      .maxByOrNull { it.createdAt ?: Instant.MIN } ?: return null

    return LatestMigrationInfo(
      latestTransaction.createdAt!!,
      latestTransaction.date.toInstant(),
    )
  }

  private fun findLatestMigrationInfoForPrison(
    accountId: Long,
    prisonCode: String,
    allTransactions: Map<Long, Transaction>,
  ): LatestMigrationInfo? {
    val transactionIds = transactionEntryRepository.findByAccountId(accountId).map { it.transactionId }.distinct()

    val latestCreatedAt = allTransactions.values
      .filter { it.id in transactionIds && it.transactionType in migrationTypes && it.prison == prisonCode }
      .mapNotNull { it.createdAt }
      .maxOrNull()
      ?: return null

    val latestTransactionDate = allTransactions.values
      .filter { it.transactionType in migrationTypes && it.prison == prisonCode }
      .filter { it.createdAt?.equals(latestCreatedAt) ?: false }
      .maxOfOrNull { it.date.toInstant() }
      ?: return null

    return LatestMigrationInfo(latestCreatedAt, latestTransactionDate)
  }
}
