package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class SyncUpdateSubscriptionRequest(
  @field:Schema(description = "The subscription end date", example = "2026-07-01", required = true)
  val endDate: LocalDate,
)
