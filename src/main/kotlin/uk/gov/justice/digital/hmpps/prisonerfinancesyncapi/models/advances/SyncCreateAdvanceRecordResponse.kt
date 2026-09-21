package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances

import java.util.UUID

class SyncCreateAdvanceRecordResponse(
  val paymentProfileId: Long,
  val advanceUuid: UUID,
)
