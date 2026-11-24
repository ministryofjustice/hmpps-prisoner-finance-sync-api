package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class SyncUpdateSubscriptionRequest(
  @field:Schema(description = "The updated subscription start date - Must be before the end date of the subscription", example = "2026-06-14", required = false)
  val startDate: LocalDate? = null,

  @field:Schema(description = "The updated subscription end date - Must be after the start date of the subscription", example = "2026-07-14", required = false)
  val endDate: LocalDate? = null,

  @field:Schema(
    description = "The comment to set for the subscription",
    example = "This is a comment",
    required = false,
  )
  val comment: String? = null,

  @field:Schema(
    description = "The user name that updated the record.",
    example = "A739XYY",
    required = true,
  )
  val updatedBy: String,
)
