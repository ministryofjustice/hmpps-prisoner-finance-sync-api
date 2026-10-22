package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class SyncUpdateSubscriptionResponse(
  @field:Schema(description = "The updated subscription end date", example = "2026-07-01")
  val endDate: LocalDate,
)
