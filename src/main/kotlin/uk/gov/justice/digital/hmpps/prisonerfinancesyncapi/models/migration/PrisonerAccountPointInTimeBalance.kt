package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDateTime

@Schema(description = "Represents a prisoner balance at a specific point in time for synchronization from a legacy system.")
data class PrisonerAccountPointInTimeBalance(
  @field:NotBlank
  @field:Schema(description = "The prison code (e.g., 'MDI') where this specific account balance is held.")
  val prisonId: String,

  @field:NotNull
  @field:Schema(description = "The account code for the prisoner account.")
  val accountCode: Int,

  @field:NotNull
  @field:Schema(description = "The account balance at the specified time.", example = "123.45")
  @field:Digits(integer = 19, fraction = 2)
  val balance: BigDecimal,

  @field:NotNull
  @field:Schema(description = "The amount on hold for the sub-account.", example = "10.00", format = "decimal")
  @field:Digits(integer = 19, fraction = 2)
  var holdBalance: BigDecimal,

  @field:NotNull
  @field:Schema(description = "The transaction ID that resulted in the last update to the prisoner balance.")
  var transactionId: Long?,

  @field:NotNull
  @field:Schema(
    description = "The local date and time from the legacy system when this balance was valid.",
    example = "2025-09-24T10:00:00",
  )
  val asOfTimestamp: LocalDateTime,
)
