package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class SyncAdvanceRepayResponse(
  @field:Schema(description = "The advance id from Prisoner Finance", example = "de2bc56c-ea73-4f3c-8a37-5a46fdb2d79a", required = true)
  val advanceId: UUID,
)
