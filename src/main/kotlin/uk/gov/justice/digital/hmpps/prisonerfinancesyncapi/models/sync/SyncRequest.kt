package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync

import java.util.UUID

interface SyncRequest {
  val requestId: UUID
  val transactionId: Long
}
