package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountCodeLookupRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.PrisonRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionEntryRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.PrisonAccountDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.PrisonerSubAccountDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.TransactionDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerBalanceDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

const val MIGRATION_CLEARING_ACCOUNT = 9999

@Service
open class LedgerQueryService(
  private val prisonRepository: PrisonRepository,
  private val accountRepository: AccountRepository,
  private val transactionRepository: TransactionRepository,
  private val transactionEntryRepository: TransactionEntryRepository,
  private val accountCodeLookupRepository: AccountCodeLookupRepository,
  private val transactionDetailsMapper: TransactionDetailsMapper,
  private val ledgerBalanceService: LedgerBalanceService,
  private val timeConversionService: TimeConversionService,
) {

  fun getPrisonerSubAccountDetails(prisonNumber: String, accountCode: Int): PrisonerSubAccountDetails? {
    val account = findPrisonerAccount(prisonNumber, accountCode)
    return account?.let {
      val (totalBalance, holdBalance) = ledgerBalanceService.calculatePrisonerAccountBalances(it)
      PrisonerSubAccountDetails(
        code = it.accountCode,
        name = it.subAccountType ?: it.name,
        prisonNumber = prisonNumber,
        balance = totalBalance,
        holdBalance = holdBalance,
      )
    }
  }

  fun listPrisonerSubAccountDetails(prisonNumber: String): List<PrisonerSubAccountDetails> = accountRepository.findByPrisonNumber(prisonNumber).map { account ->
    val (totalBalance, holdBalance) = ledgerBalanceService.calculatePrisonerAccountBalances(account)
    PrisonerSubAccountDetails(
      code = account.accountCode,
      name = account.subAccountType ?: account.name,
      prisonNumber = prisonNumber,
      balance = totalBalance,
      holdBalance = holdBalance,
    )
  }

  fun getPrisonAccountDetails(prisonId: String, accountCode: Int): PrisonAccountDetails? {
    val account = findPrisonAccount(prisonId, accountCode)
    return account?.let {
      val accountCodeLookup = accountCodeLookupRepository.findById(accountCode)
        .orElseThrow { IllegalStateException("Account code lookup not found for code: $accountCode") }

      PrisonAccountDetails(
        code = it.accountCode,
        name = it.name,
        prisonId = prisonId,
        classification = accountCodeLookup.classification,
        postingType = it.postingType.name,
        balance = ledgerBalanceService.calculateGeneralLedgerAccountBalance(it),
      )
    }
  }

  fun listPrisonAccountDetails(prisonId: String): List<PrisonAccountDetails> {
    val prison = prisonRepository.findByCode(prisonId) ?: return emptyList()
    val accounts = accountRepository.findByPrisonId(prison.id!!)

    return accounts.filter { it.accountType == AccountType.GENERAL_LEDGER }.map { account ->
      val accountCodeLookup = accountCodeLookupRepository.findById(account.accountCode)
        .orElseThrow { IllegalStateException("Account code lookup not found for code: ${account.accountCode}") }

      PrisonAccountDetails(
        code = account.accountCode,
        name = account.name,
        prisonId = prisonId,
        classification = accountCodeLookup.classification,
        postingType = account.postingType.name,
        balance = ledgerBalanceService.calculateGeneralLedgerAccountBalance(account),
      )
    }
  }

  fun listPrisonerSubAccountTransactions(prisonNumber: String, accountCode: Int): List<TransactionDetails> {
    val account = findPrisonerAccount(prisonNumber, accountCode) ?: return emptyList()

    val transactionEntries = transactionEntryRepository.findByAccountId(account.id!!)

    if (transactionEntries.isEmpty()) {
      return emptyList()
    }
    val transactionIds = transactionEntries.map { it.transactionId }.distinct()
    val transactions = transactionRepository.findAllById(transactionIds).associateBy { it.id }
    return transactionIds.mapNotNull { transactionId ->
      val transaction = transactions[transactionId] ?: return@mapNotNull null
      val entriesForTransaction = transactionEntries.filter { it.transactionId == transactionId }
      transactionDetailsMapper.mapToTransactionDetails(transaction, entriesForTransaction)
    }
  }

  fun listPrisonAccountTransactions(prisonId: String, accountCode: Int, date: LocalDate?): List<TransactionDetails> {
    val account = findPrisonAccount(prisonId, accountCode) ?: return emptyList()

    val allAccountEntries = transactionEntryRepository.findByAccountId(account.id!!)
    if (allAccountEntries.isEmpty()) {
      return emptyList()
    }

    val transactionIds = if (date != null) {
      val dateStartInstant = timeConversionService.toUtcStartOfDay(date)
      val dateEndInstant = timeConversionService.toUtcStartOfDay(date.plusDays(1))

      transactionRepository.findByDateBetween(
        Timestamp.from(dateStartInstant),
        Timestamp.from(dateEndInstant),
      )
        .map { it.id!! }
        .intersect(allAccountEntries.map { it.transactionId }.toSet())
    } else {
      allAccountEntries.map { it.transactionId }.distinct()
    }

    if (transactionIds.isEmpty()) {
      return emptyList()
    }

    val transactions = transactionRepository.findAllById(transactionIds).associateBy { it.id }
    val transactionEntries = allAccountEntries.filter { it.transactionId in transactionIds }

    return transactionIds.mapNotNull { transactionId ->
      val transaction = transactions[transactionId] ?: return@mapNotNull null
      val entriesForTransaction = transactionEntries.filter { it.transactionId == transactionId }

      if (entriesForTransaction.isNotEmpty()) {
        transactionDetailsMapper.mapToTransactionDetails(transaction, entriesForTransaction)
      } else {
        null
      }
    }
  }

  fun getTransaction(prisonNumber: String, accountCode: Int, transactionId: String): List<TransactionDetails> {
    val account = findPrisonerAccount(prisonNumber, accountCode) ?: return emptyList()
    val transaction = transactionRepository.findByUuid(UUID.fromString(transactionId)) ?: return emptyList()
    val transactionEntries = transactionEntryRepository.findByTransactionId(transaction.id!!)
    val isForThisAccount = transactionEntries.any { it.accountId == account.id }
    if (!isForThisAccount) {
      return emptyList()
    }
    return listOf(transactionDetailsMapper.mapToTransactionDetails(transaction, transactionEntries))
  }

  fun getPrisonAccountTransaction(prisonId: String, accountCode: Int, transactionId: String): List<TransactionDetails> {
    val account = findPrisonAccount(prisonId, accountCode) ?: return emptyList()
    val transaction = transactionRepository.findByUuid(UUID.fromString(transactionId)) ?: return emptyList()
    val transactionEntries = transactionEntryRepository.findByTransactionId(transaction.id!!)
    val isForThisAccount = transactionEntries.any { it.accountId == account.id }
    if (!isForThisAccount) {
      return emptyList()
    }
    return listOf(transactionDetailsMapper.mapToTransactionDetails(transaction, transactionEntries))
  }

  fun listPrisonerBalancesByEstablishment(prisonNumber: String): List<PrisonerEstablishmentBalanceDetails> {
    val prisonerAccounts = accountRepository.findByPrisonNumber(prisonNumber)

    if (prisonerAccounts.isEmpty()) {
      return emptyList()
    }

    val allEstablishmentBalances = mutableListOf<PrisonerEstablishmentBalanceDetails>()

    prisonerAccounts.forEach { account ->
      val prisonerEstablishmentBalances = ledgerBalanceService.calculatePrisonerBalancesByEstablishment(account)

      prisonerEstablishmentBalances.mapTo(allEstablishmentBalances) { balance ->
        PrisonerEstablishmentBalanceDetails(
          prisonId = balance.prisonId,
          accountCode = account.accountCode,
          totalBalance = balance.totalBalance,
          holdBalance = balance.holdBalance,
        )
      }
    }
    return allEstablishmentBalances.toList()
  }

  fun listGeneralLedgerBalances(prisonId: String): List<GeneralLedgerBalanceDetails> {
    val prison = prisonRepository.findByCode(prisonId) ?: return emptyList()
    val accounts = accountRepository.findByPrisonId(prison.id!!)

    return accounts
      .filter { it.accountType == AccountType.GENERAL_LEDGER }
      .filter { it.accountCode != MIGRATION_CLEARING_ACCOUNT }
      .map { account ->
        GeneralLedgerBalanceDetails(
          accountCode = account.accountCode,
          balance = ledgerBalanceService.calculateGeneralLedgerAccountBalance(account),
        )
      }
  }

  private fun findPrisonAccount(prisonId: String, accountCode: Int): Account? {
    val prison = prisonRepository.findByCode(prisonId) ?: return null
    return accountRepository.findByPrisonIdAndAccountCodeAndPrisonNumberIsNull(prison.id!!, accountCode)
  }

  private fun findPrisonerAccount(prisonNumber: String, accountCode: Int): Account? = accountRepository.findByPrisonNumberAndAccountCode(prisonNumber, accountCode)

  fun aggregatedLegacyBalanceByPrisoner(prisonNumber: String) = listPrisonerBalancesByEstablishment(prisonNumber)
    .fold(BigDecimal.ZERO) { acc, balance -> acc + balance.totalBalance }
}
