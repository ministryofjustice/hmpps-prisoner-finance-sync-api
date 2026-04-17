package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeAggregatedBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import java.math.BigDecimal

class MigrationValidationService {

  internal fun convertToPence(balance: BigDecimal): Int = (balance * BigDecimal.valueOf(100)).toInt()

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
}
