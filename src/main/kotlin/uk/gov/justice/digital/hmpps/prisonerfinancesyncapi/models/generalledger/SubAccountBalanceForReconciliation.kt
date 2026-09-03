package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.HoldBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPounds
import java.math.BigDecimal
import java.time.Instant

data class SubAccountBalanceForReconciliation(
  val totalBalance: BigDecimal,
  val holdBalance: BigDecimal,
  val balanceDateTime: Instant,
) {
  companion object {
    fun fromSubAccountBalanceResponse(subAccountBalanceResponse: SubAccountBalanceResponse, subAccountBalanceHoldResponse: HoldBalanceResponse) = SubAccountBalanceForReconciliation(
      totalBalance = subAccountBalanceResponse.amount.toPounds(),
      holdBalance = subAccountBalanceHoldResponse.amount.toPounds(),
      balanceDateTime = subAccountBalanceResponse.balanceDateTime,
    )
  }
}
