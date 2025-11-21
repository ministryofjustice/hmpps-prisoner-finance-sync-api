package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Balance of prison general ledger account")
data class GeneralLedgerBalanceDetails(
  @field:Schema(description = "The unique account code identifying the prison general ledger account.", example = "1021")
  val accountCode: Int,

  @field:Schema(
    description = "The current monetary balance of the prison general ledger account. This value can be positive or negative.",
    example = "1234.56",
  )
  val balance: BigDecimal = BigDecimal.ZERO,
)
