package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionEntryRepository

@Component
class PrisonerService(
  private val transactionEntryRepository: TransactionEntryRepository,
  private val accountRepository: AccountRepository,
) {

  fun merge(prisonNumberFrom: String, prisonNumberTo: String) {
    val accountsToMerge = accountRepository.findByPrisonNumber(prisonNumberFrom)

    if (accountsToMerge.isEmpty()) {
      log.info("No accounts found for prisoner $prisonNumberFrom. ")
      return
    }

    // Loop through each of the prisoners sub-accounts up tp 3 (2101, 2102, 2103)

    accountsToMerge.forEach { oldAccount ->

      // Find the account of target prisoner to merge
      val targetAccount = accountRepository.findByPrisonNumberAndAccountCode(prisonNumberTo, oldAccount.accountCode)

      if (targetAccount != null) {

        log.info("Merge account ${oldAccount.accountCode} ${oldAccount.name} to ${targetAccount.accountCode} ${targetAccount.name}")

        transactionEntryRepository.reassignEntries(oldAccount.id!!, targetAccount.id!!)
      }
    }

    return
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
