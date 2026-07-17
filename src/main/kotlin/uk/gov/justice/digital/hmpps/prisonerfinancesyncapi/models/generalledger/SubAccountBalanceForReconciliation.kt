package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger

import java.math.BigDecimal
import java.time.Instant

data class SubAccountBalanceForReconciliation(
  val totalBalance: BigDecimal,
  val holdBalance: BigDecimal,
  val balanceDateTime: Instant
) {
  companion object {
    fun fromSubAccountBalanceResponse (subAccountBalanceResponse: SubAccountBalanceResponse) =
      SubAccountBalanceForReconciliation(
        totalBalance = subAccountBalanceResponse.amount.toBigDecimal().movePointLeft(2).setScale(2),
        // hardcoded for now
        holdBalance = BigDecimal.ZERO,
        balanceDateTime = subAccountBalanceResponse.balanceDateTime
      )
  }
}
