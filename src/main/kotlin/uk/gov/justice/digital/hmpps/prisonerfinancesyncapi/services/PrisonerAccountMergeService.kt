package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Transaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.TransactionEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionEntryRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.AccountService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.TransactionService
import java.time.Instant

@Service
class PrisonerAccountMergeService(
  private val accountRepository: AccountRepository,
  private val transactionRepository: TransactionRepository,
  private val transactionEntryRepository: TransactionEntryRepository,
  private val accountService: AccountService,
  private val transactionService: TransactionService,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun consolidateAccounts(prisonNumberFrom: String, prisonNumberTo: String) {
    log.info("Starting Prisoner Account Consolidation: $prisonNumberFrom -> $prisonNumberTo")

    val accountsToConsolidate = accountRepository.findByPrisonNumber(prisonNumberFrom)
    if (accountsToConsolidate.isEmpty()) {
      log.warn("No accounts found for source prisoner: $prisonNumberFrom")
      return
    }

    accountsToConsolidate.forEach { consolidateSingleAccount(it, prisonNumberTo) }
  }

  private fun consolidateSingleAccount(fromAccount: Account, prisonNumberTo: String) {
    val toAccount = accountRepository.findByPrisonNumberAndAccountCode(
      prisonNumberTo,
      fromAccount.accountCode,
    ) ?: createTargetAccount(fromAccount, prisonNumberTo)

    log.info("Consolidating Prisoner Account ${fromAccount.id} -> ${toAccount.id}")

    val historicalEntries = transactionEntryRepository.findByAccountId(fromAccount.id!!)
      .distinctBy { it.transactionId }

    historicalEntries.forEach { entry ->
      processTransactionMigration(entry.transactionId, fromAccount, toAccount)
    }
  }

  private fun processTransactionMigration(transactionId: Long, fromAccount: Account, toAccount: Account) {
    val transaction = transactionRepository.findById(transactionId)
      .orElseThrow { IllegalStateException("Transaction not found: $transactionId") }

    val transactionEntries = transactionEntryRepository.findByTransactionId(transactionId)

    val reverseTransactionType = when (transaction.transactionType) {
      "OB", "TOB" -> "ROB"
      "OHB", "TOHB" -> "ROHB"
      else -> transaction.transactionType
    }

    val transferTransactionType = when (transaction.transactionType) {
      "OB", "TOB" -> "TOB"
      "OHB", "TOHB" -> "TOHB"
      else -> transaction.transactionType
    }

    recordReversal(transaction, transactionEntries, reverseTransactionType)
    recordTransfer(transaction, transactionEntries, fromAccount, toAccount, transferTransactionType)
  }

  private fun recordReversal(originalTxn: Transaction, entries: List<TransactionEntry>, transactionType: String) {
    transactionService.recordTransaction(
      transactionType = transactionType,
      description = "REVERSE TRANSACTION ${originalTxn.id}: ${originalTxn.description}",
      entries = entries.map { Triple(it.accountId, it.amount, it.entryType.flipped()) },
      prison = originalTxn.prison!!,
      transactionTimestamp = Instant.now(),
    )
  }

  private fun recordTransfer(originalTxn: Transaction, entries: List<TransactionEntry>, fromAccount: Account, toAccount: Account, transactionType: String) {
    val reinstatementEntries = entries.map { entry ->
      val targetAccountId = if (entry.accountId == fromAccount.id) toAccount.id!! else entry.accountId
      Triple(targetAccountId, entry.amount, entry.entryType)
    }
    transactionService.recordTransaction(
      transactionType = transactionType,
      description = "TRANSFER TRANSACTION ${originalTxn.id}: ${originalTxn.description}",
      entries = reinstatementEntries,
      prison = originalTxn.prison!!,
      transactionTimestamp = Instant.now(),
    )
  }

  private fun createTargetAccount(oldAccount: Account, newPrisonNumber: String): Account {
    log.info("Creating new account for $newPrisonNumber based on account ${oldAccount.accountCode}")
    return accountService.createAccount(
      prisonId = oldAccount.prisonId,
      name = "$newPrisonNumber - ${oldAccount.subAccountType}",
      accountType = oldAccount.accountType,
      accountCode = oldAccount.accountCode,
      postingType = oldAccount.postingType,
      prisonNumber = newPrisonNumber,
      subAccountType = oldAccount.subAccountType,
    )
  }

  private fun PostingType.flipped() = if (this == PostingType.DR) PostingType.CR else PostingType.DR
}
