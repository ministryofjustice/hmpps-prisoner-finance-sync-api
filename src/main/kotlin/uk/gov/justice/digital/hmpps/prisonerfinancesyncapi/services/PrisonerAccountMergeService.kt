package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
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
      log.info("No accounts found for $prisonNumberFrom. Consolidation skipped.")
      return
    }

    accountsToConsolidate.forEach { fromAccount ->

      // Find Target Account (Create if missing)
      val toAccount = accountRepository.findByPrisonNumberAndAccountCode(
        prisonNumberTo,
        fromAccount.accountCode,
      ) ?: createTargetAccount(fromAccount, prisonNumberTo)

      log.info("Consolidating Account ID ${fromAccount.id} -> ${toAccount.id}")

      val transactionEntries = transactionEntryRepository.findByAccountId(fromAccount.id!!)

      transactionEntries.forEach { transactionEntry ->

        val allEntriesInTxn = transactionEntryRepository.findByTransactionId(transactionEntry.transactionId)

        val originalTxn = transactionRepository.findById(transactionEntry.transactionId)
          .orElseThrow { IllegalStateException("Transaction not found: ${transactionEntry.transactionId}") }

        // Create Reverse Transaction (Zeros out Old Account)
        val reversalEntries = allEntriesInTxn.map { entry ->
          Triple(
            entry.accountId,
            entry.amount,
            flipPostingType(entry.entryType), // Flip DR <-> CR
          )
        }

        transactionService.recordTransaction(
          transactionType = originalTxn.transactionType, // TODO: What transaction type should we use?
          description = "REVERSE TRANSACTION: ${originalTxn.description}",
          entries = reversalEntries,
          prison = originalTxn.prison!!,
          createdAt = Instant.now(), // TODO: What should the timestamp be?
        )

        // Swap the From account ID for the To ID
        val reinstatementEntries = allEntriesInTxn.map { entry ->
          val targetAccountId = if (entry.accountId == fromAccount.id) {
            toAccount.id!!
          } else {
            entry.accountId // Keep General Ledger account the same
          }

          Triple(
            targetAccountId,
            entry.amount,
            entry.entryType,
          )
        }

        transactionService.recordTransaction(
          transactionType = originalTxn.transactionType,
          description = "MERGE TRANSFER: ${originalTxn.description}",
          entries = reinstatementEntries,
          prison = originalTxn.prison,
          createdAt = Instant.now(),
        )
      }
    }
  }

  private fun createTargetAccount(oldAccount: Account, newPrisonNumber: String): Account {
    log.info("Creating new account for $newPrisonNumber based on account ${oldAccount.accountCode}")
    return accountService.createAccount(
      prisonId = oldAccount.prisonId,
      name = "$newPrisonNumber - ${oldAccount.subAccountType!!}",
      accountType = oldAccount.accountType,
      accountCode = oldAccount.accountCode,
      postingType = oldAccount.postingType,
      prisonNumber = newPrisonNumber,
      subAccountType = oldAccount.subAccountType,
    )
  }

  private fun flipPostingType(type: PostingType): PostingType = if (type == PostingType.DR) PostingType.CR else PostingType.DR
}
