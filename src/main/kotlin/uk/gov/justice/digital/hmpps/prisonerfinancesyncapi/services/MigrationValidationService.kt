package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence

class MigrationValidationService(val generalLedgerService: GeneralLedgerService) {

  val balanceAggregationService = BalanceAggregationService()

  val prisonerSubAccounts = mapOf(2101 to "CASH", 2102 to "SPENDS", 2103 to "SAVINGS")

  fun validatePrisonerBalances(prisonNumber: String, accountBalances: List<PrisonerAccountPointInTimeBalance>): Boolean {
    val subAccountBalances = generalLedgerService.getGLPrisonerBalances(prisonNumber)
    val aggregatedBalances = balanceAggregationService.aggregateBalances(accountBalances)

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
