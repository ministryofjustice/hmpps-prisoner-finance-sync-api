package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeAggregatedBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import java.math.BigDecimal

class MigrationValidationService(val generalLedgerService: GeneralLedgerService) {

  internal fun convertToPence(balance: BigDecimal): Long = (balance * BigDecimal.valueOf(100)).toLong()

  fun aggregateBalances(accountBalances: List<PrisonerAccountPointInTimeBalance>): List<PrisonerAccountPointInTimeAggregatedBalance> {
    val aggregatedBalances = mutableMapOf<Int, PrisonerAccountPointInTimeAggregatedBalance>()

    accountBalances.forEach {
      if (aggregatedBalances[it.accountCode] == null) {
        aggregatedBalances[it.accountCode] = PrisonerAccountPointInTimeAggregatedBalance(
          accountCode = it.accountCode,
          balance = convertToPence(it.balance),
          holdBalance = convertToPence(it.holdBalance),
        )
      } else {
        val current = aggregatedBalances[it.accountCode]!!
        aggregatedBalances[it.accountCode] = PrisonerAccountPointInTimeAggregatedBalance(
          accountCode = current.accountCode,
          balance = convertToPence(it.balance) + current.balance,
          holdBalance = convertToPence(it.holdBalance) + current.holdBalance,
        )
      }
    }
    return aggregatedBalances.values.toList()
  }

  val prisonerSubAccounts = mapOf(2101 to "CASH", 2102 to "SPENDS", 2103 to "SAVINGS")

  fun validatePrisonerBalances(prisonNumber: String, accountBalances: List<PrisonerAccountPointInTimeBalance>): Boolean {
    val subAccountBalances = generalLedgerService.getGLPrisonerBalances(prisonNumber)
    val aggregatedBalances = aggregateBalances(accountBalances)

    var validated = true

    aggregatedBalances.forEach { aggregatedBalance ->

      val subAccountRef = prisonerSubAccounts[aggregatedBalance.accountCode]
      val glBalance = subAccountBalances[subAccountRef]?.amount

      if (glBalance != aggregatedBalance.balance) {
        validated = false
      }
    }

    return validated
  }
}
