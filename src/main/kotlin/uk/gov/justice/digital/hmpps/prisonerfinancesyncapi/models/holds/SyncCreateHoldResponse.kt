package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds

import java.util.UUID

class SyncCreateHoldResponse(
  val holdNumber: Long,
  val holdUuid: UUID,
)
