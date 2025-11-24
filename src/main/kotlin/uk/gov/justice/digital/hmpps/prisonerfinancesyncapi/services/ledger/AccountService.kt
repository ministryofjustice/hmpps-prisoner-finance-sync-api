package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountCodeLookupRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository

@Service
open class AccountService(
  private val accountRepository: AccountRepository,
  private val accountCodeLookupRepository: AccountCodeLookupRepository,
) {
  fun findGeneralLedgerAccount(prisonId: Long, accountCode: Int): Account? = accountRepository.findByPrisonIdAndAccountCodeAndPrisonNumberIsNull(prisonId, accountCode)

  @Transactional
  open fun resolveAccount(accountCode: Int, prisonNumber: String, prisonId: Long): Account {
    val accountCodeLookup = accountCodeLookupRepository.findById(accountCode)
      .orElseThrow { IllegalArgumentException("Account code lookup not found for code: $accountCode") }

    val subAccountType = getSubAccountTypeFromCode(accountCode)

    return if (subAccountType != null) {
      accountRepository.findByPrisonNumberAndSubAccountType(prisonNumber, subAccountType)
        ?: createAccount(
          prisonId = null,
          name = "$prisonNumber - $subAccountType",
          accountType = AccountType.PRISONER,
          prisonNumber = prisonNumber,
          subAccountType = subAccountType,
          accountCode = accountCode,
          postingType = accountCodeLookup.postingType,
        )
    } else {
      accountRepository.findByPrisonIdAndAccountCodeAndPrisonNumberIsNull(prisonId, accountCode)
        ?: createGeneralLedgerAccount(
          prisonId = prisonId,
          accountCode = accountCode,
        )
    }
  }

  @Transactional
  open fun createGeneralLedgerAccount(
    prisonId: Long,
    accountCode: Int,
  ): Account {
    val accountCodeLookup = accountCodeLookupRepository.findById(accountCode)
      .orElseThrow { IllegalArgumentException("Account code lookup not found for code: $accountCode") }

    return createAccount(
      prisonId = prisonId,
      name = accountCodeLookup.name,
      accountCode = accountCode,
      accountType = AccountType.GENERAL_LEDGER,
      postingType = accountCodeLookup.postingType,
    )
  }

  @Transactional
  open fun createAccount(
    prisonId: Long?,
    name: String,
    accountType: AccountType,
    accountCode: Int,
    postingType: PostingType,
    prisonNumber: String? = null,
    subAccountType: String? = null,
  ): Account {
    if (accountType == AccountType.PRISONER && prisonNumber == null) {
      throw IllegalArgumentException("Offender display ID is mandatory for PRISONER accounts.")
    }

    val account = Account(
      prisonId = prisonId,
      name = name,
      accountType = accountType,
      accountCode = accountCode,
      prisonNumber = prisonNumber,
      subAccountType = subAccountType,
      postingType = postingType,
    )
    return accountRepository.save(account)
  }

  fun getSubAccountTypeFromCode(accountCode: Int): String? = when (accountCode) {
    2101 -> "Cash"
    2102 -> "Spends"
    2103 -> "Savings"
    else -> null
  }
}
