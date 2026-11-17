package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class SyncUpdateSubscriptionResponse(
  @field:Schema(description = "The updated subscription start date", example = "2026-06-14")
  val startDate: LocalDate?,

  @field:Schema(description = "The updated subscription end date", example = "2026-07-14")
  val endDate: LocalDate?,
)
