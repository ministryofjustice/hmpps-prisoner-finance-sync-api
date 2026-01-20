package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models

import java.time.Instant
import java.util.UUID

data class NomisSyncPayloadDto(
  val legacyTransactionId: Long?,
  val synchronizedTransactionId: UUID,
  val caseloadId: String?,
  val timestamp: Instant,
  val requestTypeIdentifier: String?,
  val requestId: UUID,
  val transactionTimestamp: Instant?,
)
