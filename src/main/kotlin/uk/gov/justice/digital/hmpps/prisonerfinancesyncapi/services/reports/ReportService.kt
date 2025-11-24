package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.reports

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.TransactionType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionEntryRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionTypeRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.reports.SummaryOfPaymentAndReceiptsReport
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class ReportService(
  private val accountRepository: AccountRepository,
  private val transactionRepository: TransactionRepository,
  private val transactionEntryRepository: TransactionEntryRepository,
  private val transactionTypeRepository: TransactionTypeRepository,
) {

  fun generateDailyPrisonSummaryReport(
    prisonId: String,
    date: LocalDate,
  ): List<SummaryOfPaymentAndReceiptsReport.PostingReportEntry> {
    val dateStart = date.atStartOfDay(ZoneOffset.UTC)
    val dateEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC)

    val dailyTransactionEntries = transactionEntryRepository.findByDateBetweenAndPrisonCode(
      Timestamp.from(dateStart.toInstant()),
      Timestamp.from(dateEnd.toInstant()),
      prisonId,
    )

    if (dailyTransactionEntries.isEmpty()) {
      return emptyList()
    }

    val transactionIds = dailyTransactionEntries.map { it.transactionId }.distinct()
    val transactions = transactionRepository.findAllById(transactionIds).associateBy { it.id }

    val accountIds = dailyTransactionEntries.map { it.accountId }.distinct()
    val prisonAccounts = accountRepository.findAllById(accountIds).associateBy { it.id }

    val transactionTypeNames = transactions.values.map { it.transactionType }.distinct()
    val transactionTypes = transactionTypeRepository.findByTxnTypeIn(transactionTypeNames).associateBy { it.txnType }

    return dailyTransactionEntries
      .mapNotNull { entry ->
        val transaction = transactions[entry.transactionId]
        val account = prisonAccounts[entry.accountId]

        if (transaction == null || account == null) {
          return@mapNotNull null
        }

        val tempPosting = SummaryOfPaymentAndReceiptsReport.PostingReportEntry(
          date = date,
          businessDate = date,
          type = transaction.transactionType,
          description = transactionTypes[transaction.transactionType]?.description ?: "Unknown",
          transactionUsage = getTransactionUsage(transactionTypes[transaction.transactionType]),
          private = BigDecimal.ZERO,
          spending = BigDecimal.ZERO,
          saving = BigDecimal.ZERO,
          credits = BigDecimal.ZERO,
          debits = BigDecimal.ZERO,
          total = BigDecimal.ZERO,
        )

        // Distribute the transaction amount across the sub-accounts (private, spending, etc.).
        if (account.prisonNumber != null) {
          when (account.accountCode) {
            2101 -> tempPosting.private += entry.amount
            2102 -> tempPosting.spending += entry.amount
            2103 -> tempPosting.saving += entry.amount
          }
        }
        tempPosting
      }
      // Group entries by transaction type to sum up all movements.
      .groupBy { it.type }
      .map { (type, entries) ->
        val firstEntry = entries.first()
        val totalPrivate = entries.sumOf { it.private }
        val totalSpending = entries.sumOf { it.spending }
        val totalSaving = entries.sumOf { it.saving }
        val totalMovement = totalPrivate + totalSpending + totalSaving

        // Assign the total movement to the appropriate credits or debits column.
        val credits = if (firstEntry.transactionUsage == "Receipts") totalMovement else BigDecimal.ZERO
        val debits = if (firstEntry.transactionUsage == "Payments") totalMovement else BigDecimal.ZERO

        SummaryOfPaymentAndReceiptsReport.PostingReportEntry(
          date = firstEntry.date,
          businessDate = firstEntry.businessDate,
          transactionUsage = firstEntry.transactionUsage,
          type = type,
          description = firstEntry.description,
          private = totalPrivate,
          spending = totalSpending,
          saving = totalSaving,
          credits = credits,
          debits = debits,
          total = totalMovement,
        )
      }
      .sortedWith(compareBy<SummaryOfPaymentAndReceiptsReport.PostingReportEntry> { it.transactionUsage }.thenBy { it.description })
  }

  private fun getTransactionUsage(transactionType: TransactionType?): String = when (transactionType?.txnUsage) {
    "R", "ADV", "C" -> "Receipts"
    "D", "O" -> "Payments"
    "F" -> "Fees"
    else -> "Unknown"
  }
}
