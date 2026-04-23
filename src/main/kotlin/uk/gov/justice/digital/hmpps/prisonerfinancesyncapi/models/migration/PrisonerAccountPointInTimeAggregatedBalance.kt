package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

@Schema(description = "Represents an aggregated prisoner balance at a specific point in time for validation of migration from a legacy system.")
data class PrisonerAccountPointInTimeAggregatedBalance(

  @field:Schema(description = "The account code for the prisoner account.")
  val accountCode: Int,

  @field:Schema(description = "The account balance at the specified time.")
  val balance: BigDecimal,

  @field:Schema(description = "The amount on hold for the sub-account.")
  var holdBalance: BigDecimal,

  @field:Schema(description = "The transaction ID that resulted in the last update to the prisoner balance.")
  var transactionId: Long?,

  @field:Schema(
    description = "The local date and time from the legacy system when this balance was valid.",
    example = "2025-09-24T10:00:00",
  )
  val asOfTimestamp: LocalDateTime,
)
