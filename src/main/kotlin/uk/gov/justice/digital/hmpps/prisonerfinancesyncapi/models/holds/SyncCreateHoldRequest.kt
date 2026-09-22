package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Digits
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "Create Hold Request")
data class SyncCreateHoldRequest(

  @field:Schema(
    description = "The prison number that the hold belongs to",
    example = "19228028",
    required = true,
    nullable = false,
  )
  val prisonNumber: String,

  @field:Schema(
    description = "The general ledger account code",
    example = "2101",
    required = true,
    nullable = false,
  )
  val subAccountCode: Int,

  @field:Schema(
    description = "The hold number",
    example = "123456789",
    required = true,
    nullable = false,
  )
  val holdNumber: Long,

  @field:Schema(
    description = "The date and time the transaction was created.",
    example = "2024-06-18T14:30:00.123456",
    nullable = false,
    required = true,
  )
  val createdAt: LocalDateTime,

  @field:Schema(
    description = "The user id of the person who created the transaction.",
    example = "JD12345",
    required = true,
  )
  val createdBy: String,

  @field:Schema(
    description = "The date and time the hold applies from.",
    example = "2024-06-18T14:30:00.123456",
    nullable = false,
    required = true,
  )
  val holdFromDate: LocalDateTime,

  @field:Schema(
    description = "The date and time the hold applies until.",
    example = "2024-06-20T14:30:00.123456",
    nullable = true,
    required = false,
  )
  val holdUntilDate: LocalDateTime? = null,

  @field:Schema(
    description = "A flag to indicate if the hold has been released.",
    nullable = false,
    required = false,
  )
  val isReleased: Boolean,

  @field:Schema(
    description = "The description on the hold transaction.",
    nullable = true,
    required = false,
  )
  val description: String? = null,

  @field:Schema(
    description = "The hold type of the hold transaction.",
    example = "WHF",
    nullable = false,
    required = true,
  )
  val holdType: String,

  @field:Schema(
    description = "The prison code of the location that initiated the transaction.",
    example = "LEI",
    nullable = false,
    required = true,
  )
  val holdLocation: String,

  @field:Schema(
    description = "The amount to hold.",
    example = "10.23",
    nullable = false,
    required = true,
  )
  @field:Digits(integer = 19, fraction = 2)
  val amount: BigDecimal,

  @field:Schema(
    description = "The transaction id of the hold transaction.",
    nullable = false,
    required = true,
  )
  val holdTransactionId: Long,

  @field:Schema(
    description = "The transaction id of the hold release transaction.",
    nullable = true,
    required = false,
  )
  val releaseTransactionId: Long? = null,

) {
  fun toCreateHoldRequest(
    subAccountRef: CreateHoldRequest.SubAccountRef,
    createdAt: Instant,
    holdFromDate: Instant,
    holdUntilDate: Instant? = null,
    prisonSubAccountId: UUID,
    prisonerSubAccountId: UUID,
  ) = CreateHoldRequest(
    prisonNumber = this.prisonNumber,
    legacyHoldNumber = this.holdNumber,
    subAccountRef = subAccountRef,
    createdAt = createdAt,
    createdBy = this.createdBy,
    holdFromDate = holdFromDate,
    isReleased = this.isReleased,
    holdType = CreateHoldRequest.HoldType.valueOf(this.holdType.uppercase()),
    amount = this.amount.toPence(),
    holdLocation = this.holdLocation,
    holdUntilDate = holdUntilDate,
    description = this.description,
    prisonSubAccountId = prisonSubAccountId,
    prisonerSubAccountId = prisonerSubAccountId,
    holdLegacyTransactionId = this.holdTransactionId,
  )
}
