package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class SyncUpdateSubscriptionRequest(
  @field:Schema(description = "The updated subscription start date - Must be before the end date of the subscription", example = "2026-06-14", required = false)
  val startDate: LocalDate?,

  @field:Schema(description = "The updated subscription end date - Must be after the start date of the subscription", example = "2026-07-14", required = false)
  val endDate: LocalDate?,
)
