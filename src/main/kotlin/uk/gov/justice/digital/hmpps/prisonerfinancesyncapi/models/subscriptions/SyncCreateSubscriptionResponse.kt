package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

class SyncCreateSubscriptionResponse(
  @field:Schema(description = "The subscription ID in Prisoner Finance", example = "de2bc56c-ea73-4f3c-8a37-5a46fdb2d79b")
  val subscriptionId: UUID,
)
