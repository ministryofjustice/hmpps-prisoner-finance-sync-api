package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeAggregatedBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import kotlin.collections.forEach

class BalanceAggregationService {
  fun aggregateBalances(accountBalances: List<PrisonerAccountPointInTimeBalance>): List<PrisonerAccountPointInTimeAggregatedBalance> {
    val aggregatedBalances = mutableMapOf<Int, PrisonerAccountPointInTimeAggregatedBalance>()

    accountBalances.forEach {
      if (aggregatedBalances[it.accountCode] == null) {
        aggregatedBalances[it.accountCode] = PrisonerAccountPointInTimeAggregatedBalance(
          accountCode = it.accountCode,
          balance = it.balance,
          holdBalance = it.holdBalance,
          transactionId = it.transactionId,
          asOfTimestamp = it.asOfTimestamp,
        )
      } else {
        val existingAggregatedBalance = aggregatedBalances[it.accountCode]!!

        val latestTimestamp = maxOf(it.asOfTimestamp, existingAggregatedBalance.asOfTimestamp)
        val latestTransactionId = if (latestTimestamp == it.asOfTimestamp) it.transactionId else existingAggregatedBalance.transactionId

        aggregatedBalances[it.accountCode] = PrisonerAccountPointInTimeAggregatedBalance(
          accountCode = existingAggregatedBalance.accountCode,
          balance = it.balance + existingAggregatedBalance.balance,
          holdBalance = it.holdBalance + existingAggregatedBalance.holdBalance,
          transactionId = latestTransactionId,
          asOfTimestamp = latestTimestamp,
        )
      }
    }
    return aggregatedBalances.values.toList()
  }
}
