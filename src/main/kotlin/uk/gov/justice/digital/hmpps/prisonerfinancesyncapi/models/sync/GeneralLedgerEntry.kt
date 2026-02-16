package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Digits
import java.math.BigDecimal

@Schema(description = "Represents a general ledger entry.")
data class GeneralLedgerEntry(
  @field:Schema(description = "The sequence number for this specific general ledger entry.", example = "1", required = true)
  val entrySequence: Int,

  @field:Schema(description = "The general ledger account code.", example = "2101", required = true)
  val code: Int,

  @field:Schema(description = "Indicates whether the entry is a Debit (DR) or Credit (CR).", allowableValues = ["DR", "CR"], example = "DR", required = true)
  val postingType: String,

  @field:Schema(description = "The monetary amount of the general ledger entry.", example = "162.00", required = true)
  @field:Digits(integer = 19, fraction = 2)
  val amount: BigDecimal,
)
