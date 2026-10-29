package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AccountAdvanceBalanceResponse
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
)

data class AccountReconciliationResponse(

  val advances: AdvancesAccountReconciliation,
  val subscriptions: SubscriptionsAccountReconciliation,

  // this will ignore the balances field, and just put the subaccount balances in its place
  @JsonIgnore
  val subAccountBalances: Map<String, SubAccountBalanceForReconciliation> = emptyMap(),
) {
  @JsonAnyGetter
  fun getDynamicSubAccountBalances(): Map<String, SubAccountBalanceForReconciliation> = subAccountBalances
}
