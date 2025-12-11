package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionEntryRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionRepository
import java.time.Instant

@Component
class PrisonerService(
  private val transactionEntryRepository: TransactionEntryRepository,
  private val accountRepository: AccountRepository,
  private val transactionRepository: TransactionRepository,
) {

  @Transactional
  fun merge(prisonNumberFrom: String, prisonNumberTo: String) {
    val accountsToMerge = accountRepository.findByPrisonNumber(prisonNumberFrom)

    if (accountsToMerge.isEmpty()) {
      log.info("No accounts found for prisoner $prisonNumberFrom. ")
      return
    }

    // Loop through each of the prisoners sub-accounts up tp 3 (2101, 2102, 2103)

    accountsToMerge.forEach { fromAccount ->

      // Find the account of target prisoner to merge
      val toAccount = accountRepository.findByPrisonNumberAndAccountCode(prisonNumberTo, fromAccount.accountCode)

      if (toAccount != null) {
        log.info("Merge account ${fromAccount.accountCode} ${fromAccount.name} to ${toAccount.accountCode} ${toAccount.name}")

        transactionRepository.recordTransactionsMerged(toAccount.id!!, Instant.now())

        transactionEntryRepository.reassignEntries(fromAccount.id!!, toAccount.id)
      }
    }

    return
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
