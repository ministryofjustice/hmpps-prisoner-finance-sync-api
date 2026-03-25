package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.PrisonRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerBalanceDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetails
import java.math.BigDecimal

const val MIGRATION_CLEARING_ACCOUNT = 9999

@Service
open class LedgerQueryService(
  private val prisonRepository: PrisonRepository,
  private val accountRepository: AccountRepository,
  private val ledgerBalanceService: LedgerBalanceService,
) {

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

  fun aggregatedLegacyBalanceForAccountCode(accountCode: Int, balances: List<PrisonerEstablishmentBalanceDetails>) = balances
    .filter { it.accountCode == accountCode }
    .fold(BigDecimal.ZERO) { acc, balance -> acc + balance.totalBalance }
    .movePointRight(2).toLong()
}
