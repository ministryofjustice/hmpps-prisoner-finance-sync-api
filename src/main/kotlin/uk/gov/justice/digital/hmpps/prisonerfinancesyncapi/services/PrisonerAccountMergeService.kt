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
    }

    accountsToConsolidate.forEach { fromAccount ->
      consolidateSingleAccount(fromAccount, prisonNumberTo)
    }
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
    val originalTxn = transactionRepository.findById(transactionId)
      .orElseThrow { IllegalStateException("Transaction not found: $transactionId") }

    val allEntriesInTxn = transactionEntryRepository.findByTransactionId(transactionId)

    // Remap Opening Balance 'OB' to Sub Account Transfer 'OT
    val transactionType = if (originalTxn.transactionType == "OB") {
      "OT"
    } else {
      originalTxn.transactionType
    }

    // 1. REVERSAL: Use ORIGINAL type
    recordReversal(originalTxn, allEntriesInTxn, transactionType)

    // 2. TRANSFER: Use SAFE type
    recordTransfer(originalTxn, allEntriesInTxn, fromAccount, toAccount, transactionType)
  }

  private fun recordReversal(
    originalTxn: Transaction,
    entries: List<TransactionEntry>,
    transactionType: String,
  ) {
    val reversalEntries = entries.map { entry ->
      Triple(
        entry.accountId,
        entry.amount,
        flipPostingType(entry.entryType),
      )
    }

    transactionService.recordTransaction(
      transactionType = transactionType,
      description = "REVERSE TRANSACTION ${originalTxn.id}: ${originalTxn.description}",
      entries = reversalEntries,
      prison = originalTxn.prison!!,
      transactionTimestamp = Instant.now(), // Ensure date is recorded as Today
    )
  }

  private fun recordTransfer(
    originalTxn: Transaction,
    entries: List<TransactionEntry>,
    fromAccount: Account,
    toAccount: Account,
    transactionType: String,
  ) {
    val reinstatementEntries = entries.map { entry ->
      val targetAccountId = if (entry.accountId == fromAccount.id) {
        toAccount.id!!
      } else {
        entry.accountId
      }

      Triple(
        targetAccountId,
        entry.amount,
        entry.entryType,
      )
    }

    transactionService.recordTransaction(
      transactionType = transactionType,
      description = "MERGE TRANSFER ${originalTxn.id}: ${originalTxn.description}",
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

  private fun flipPostingType(type: PostingType): PostingType = if (type == PostingType.DR) PostingType.CR else PostingType.DR
}
