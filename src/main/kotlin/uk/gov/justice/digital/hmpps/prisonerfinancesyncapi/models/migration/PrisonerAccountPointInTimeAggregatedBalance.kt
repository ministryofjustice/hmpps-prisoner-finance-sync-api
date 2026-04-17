package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Represents an aggregated prisoner balance at a specific point in time for validation of migration from a legacy system.")
data class PrisonerAccountPointInTimeAggregatedBalance(

  @field:Schema(description = "The account code for the prisoner account.")
  val accountCode: Int,

  @field:Schema(description = "The account balance at the specified time.")
  val balance: Long,

  @field:Schema(description = "The amount on hold for the sub-account.")
  var holdBalance: Long,
)
