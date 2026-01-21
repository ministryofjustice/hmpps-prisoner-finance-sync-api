package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit

import java.time.Instant
import java.util.UUID

interface NomisSyncPayloadSummary {
  val legacyTransactionId: Long?
  val synchronizedTransactionId: UUID
  val caseloadId: String?
  val timestamp: Instant
  val requestTypeIdentifier: String?
  val requestId: UUID
  val transactionTimestamp: Instant?
}
