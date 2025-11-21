package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Summary of a prison general ledger account")
data class PrisonAccountDetails(
  @field:Schema(description = "The unique numeric code identifying the prison general ledger account.", example = "1000")
  override val code: Int,

  @field:Schema(
    description = "The human-readable name of the prison general ledger account (e.g., 'Cash', 'Bank Account', 'Accounts Payable').",
    example = "Cash",
  )
  override val name: String,

  @field:Schema(
    description = "The prison id that owns this general ledger account.",
    example = "LEI",
  )
  val prisonId: String,

  @field:Schema(
    description = "The classification of the prison general ledger account. Values include 'Asset', 'Liability', 'Receipt', and 'Disbursement'.",
    example = "Asset",
    allowableValues = ["Asset", "Liability", "Receipt", "Disbursement"],
  )
  val classification: String,

  @field:Schema(
    description = "The natural transaction posting type for this prison general ledger account. 'DR' indicates a Debit posting, while 'CR' indicates a Credit posting.",
    example = "DR",
    allowableValues = ["DR", "CR"],
  )
  val postingType: String,

  @field:Schema(
    description = "The current monetary balance of the prison general ledger account. This value can be positive or negative.",
    example = "1234.56",
  )
  override var balance: BigDecimal = BigDecimal.ZERO,
) : AccountDetails
