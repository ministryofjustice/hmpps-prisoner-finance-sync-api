package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence

@Service
class MigrationValidationService(
  private val generalLedgerService: GeneralLedgerService,
) {

  val prisonerSubAccounts = mapOf(2101 to "CASH", 2102 to "SPENDS", 2103 to "SAVINGS")

  fun validatePrisonerBalances(prisonNumber: String, accountBalances: List<PrisonerAccountPointInTimeBalance>): Boolean {
    val aggregatedBalances = BalanceAggregator.aggregateBalances(accountBalances)
    val subAccountBalances = generalLedgerService.getGLPrisonerBalances(prisonNumber)

    var validated = true

    aggregatedBalances.forEach { (accountCode, aggregatedBalance) ->

      val subAccountRef = prisonerSubAccounts[accountCode]
      val glBalance = subAccountBalances[subAccountRef]?.amount

      if (glBalance != aggregatedBalance.balance.toPence()) {
        validated = false
      }
    }

    return validated
  }
}
