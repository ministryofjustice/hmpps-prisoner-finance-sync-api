package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AccountAdvanceBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.AccountSubscriptionSummaryResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPounds
import java.math.BigDecimal

data class AdvancesAccountReconciliation(
  val advanceCount: Int,
  val advanceBalance: BigDecimal,
) {
  companion object {
    fun fromAccountAdvanceBalanceResponse(response: AccountAdvanceBalanceResponse) = AdvancesAccountReconciliation(
      advanceCount = response.advanceCount,
      advanceBalance = response.balancesOutstanding.toPounds(),
    )
  }
}

data class SubscriptionsAccountReconciliation(
  val subscriptionCount: Int,
  val weeklyPaymentAmount: BigDecimal,
) {
  companion object {
    fun fromAccountSubscriptionSummaryResponse(response: AccountSubscriptionSummaryResponse) = SubscriptionsAccountReconciliation(
      subscriptionCount = response.subscriptionCount,
      weeklyPaymentAmount = response.weeklyPaymentAmount.toPounds(),
    )
  }
}

data class AccountReconciliationResponse(

  val advances: AdvancesAccountReconciliation,
  val subscriptions: SubscriptionsAccountReconciliation,
  val subAccountBalances: Map<String, SubAccountBalanceForReconciliation> = emptyMap(),
)
