package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Summary of a prisoner's sub account balance at a specific establishment.")
data class PrisonerEstablishmentBalanceDetails(
  @field:Schema(
    description = "The establishment code where the transactions for this balance occurred.",
    example = "LEI",
  )
  val prisonId: String,

  @field:Schema(description = "The unique numeric code identifying the sub account.", example = "2101")
  val accountCode: Int,

  @field:Schema(
    description = "The current monetary balance of the sub account at this establishment. This value can be positive or negative.",
    example = "32.00",
  )
  val totalBalance: BigDecimal,

  @field:Schema(
    description = "The total amount on hold for this sub account at this establishment.",
    example = "8.00",
  )
  val holdBalance: BigDecimal,
)
