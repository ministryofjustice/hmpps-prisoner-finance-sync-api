package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import java.time.Instant
import java.util.UUID

data class NomisSyncPayloadDto(
  val timestamp: Instant,
  val legacyTransactionId: Long?,
  val synchronizedTransactionId: UUID,
  val requestId: UUID,
  val caseloadId: String?,
  val requestTypeIdentifier: String?,
  val transactionTimestamp: Instant?,
)

fun NomisSyncPayload.toDto() = NomisSyncPayloadDto(
  timestamp = timestamp,
  legacyTransactionId = legacyTransactionId,
  synchronizedTransactionId = synchronizedTransactionId,
  requestId = requestId,
  caseloadId = caseloadId,
  requestTypeIdentifier = requestTypeIdentifier,
  transactionTimestamp = transactionTimestamp,
)

@Schema(description = "A list of general ledger account balances")
data class PayloadTransactionDetailsList(val items: List<NomisSyncPayloadDto>)
