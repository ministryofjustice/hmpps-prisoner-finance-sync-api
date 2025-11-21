package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Summary of a prisoner sub account")
data class PrisonerSubAccountDetails(
  @field:Schema(description = "The unique numeric code identifying the sub account.", example = "1000")
  override val code: Int,

  @field:Schema(
    description = "The human-readable name of the sub account (e.g., 'Private Cash', 'Savings', 'Spends').",
    example = "Savings",
  )
  override val name: String,

  @field:Schema(
    description = "Prison number of the prisoner. Also referred to as the offender number, offender id or NOMS id",
    example = "Z9090ZZ",
  )
  val prisonNumber: String,

  @field:Schema(
    description = "The current monetary balance of the sub account. This value can be positive or negative.",
    example = "1234.56",
  )
  override var balance: BigDecimal = BigDecimal.ZERO,

  @field:Schema(
    description = "The total amount on hold for this sub account",
    example = "10.00",
  )
  var holdBalance: BigDecimal = BigDecimal.ZERO,
) : AccountDetails
