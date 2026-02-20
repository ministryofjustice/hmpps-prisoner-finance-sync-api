package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Pattern
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_MESSAGE_PRISONER_ID
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_MESSAGE_TRANSACTION_TYPE
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_REGEX_PRISONER_ID
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_REGEX_TRANSACTION_TYPE
import java.math.BigDecimal

@Schema(description = "Represents a single offender transaction entry, including related general ledger entries.")
data class OffenderTransaction(
  @field:Schema(description = "The sequence number for this specific offender transaction entry.", example = "1", required = true)
  val entrySequence: Int,

  @field:Schema(description = "The internal ID of the offender.", example = "1015388", required = true)
  val offenderId: Long,

  @field:Pattern(
    regexp = VALIDATION_REGEX_PRISONER_ID,
    message = VALIDATION_MESSAGE_PRISONER_ID,
  )
  @field:Schema(description = "The display ID for the offender.", example = "A1234AA", required = true)
  val offenderDisplayId: String,

  @field:Schema(description = "The booking ID of the offender transaction.", example = "455987", required = false, nullable = true)
  val offenderBookingId: Long?,

  @field:Schema(description = "The sub-account type (e.g., REG, SPND, SAV).", example = "REG", required = true)
  val subAccountType: String,

  @field:Schema(description = "The type of posting (DR/CR).", allowableValues = ["DR", "CR"], example = "DR", required = true)
  val postingType: String,

  @field:Pattern(
    regexp = VALIDATION_REGEX_TRANSACTION_TYPE,
    message = VALIDATION_MESSAGE_TRANSACTION_TYPE,
  )
  @field:Schema(description = "The type of transaction (e.g., OT, DISCP).", example = "OT", required = true)
  val type: String,

  @field:Schema(description = "A description of the transaction entry.", example = "Sub-Account Transfer", required = true)
  val description: String,

  @field:Schema(description = "The monetary amount of the transaction entry.", example = "162.00", required = true)
  @field:Digits(integer = 19, fraction = 2)
  val amount: BigDecimal,

  @field:Schema(description = "An optional reference number for the transaction.", example = "", nullable = true)
  val reference: String?,

  @field:Schema(
    description = "A list of general ledger entries associated with this offender transaction.",
    format = "decimal",
    example = "162.00",
    required = true,
  )
  @field:Valid
  val generalLedgerEntries: List<GeneralLedgerEntry>,
)
