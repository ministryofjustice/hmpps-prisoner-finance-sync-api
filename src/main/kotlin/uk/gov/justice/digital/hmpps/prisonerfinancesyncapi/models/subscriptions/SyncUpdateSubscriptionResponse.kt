package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions

import java.time.LocalDate

data class SyncUpdateSubscriptionResponse(
  val endDate: LocalDate,
)
