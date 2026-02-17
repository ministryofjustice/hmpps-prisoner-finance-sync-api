package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import java.time.Instant
import java.util.UUID

data class NomisSyncPayloadDetail(
  val timestamp: Instant,
  val legacyTransactionId: Long?,
  val synchronizedTransactionId: UUID,
  val requestId: UUID,
  val caseloadId: String?,
  val requestTypeIdentifier: String?,
  val transactionTimestamp: Instant?,
  val transactionType: String,
  val body: String,
)

fun NomisSyncPayload.toDetail() = NomisSyncPayloadDetail(
  timestamp = timestamp,
  legacyTransactionId = legacyTransactionId,
  synchronizedTransactionId = synchronizedTransactionId,
  requestId = requestId,
  caseloadId = caseloadId,
  requestTypeIdentifier = requestTypeIdentifier,
  transactionTimestamp = transactionTimestamp,
  transactionType = transactionType,
  body = body,
)
