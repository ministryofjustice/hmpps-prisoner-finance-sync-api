package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.TransactionSyncStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.JsonComparator
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.SyncQueryService

@Component
class SyncStatusResolver(
  private val syncQueryService: SyncQueryService,
  private val jsonComparator: JsonComparator,
  private val objectMapper: ObjectMapper,
) {
  fun check(request: SyncRequest): TransactionSyncStatus {
    // Check if we have already received this request id
    val existingPayloadByRequestId = syncQueryService.findByRequestId(request.requestId)
    if (existingPayloadByRequestId != null) {
      return TransactionSyncStatus.Duplicate(existingPayloadByRequestId.synchronizedTransactionId)
    }

    // Check if we already received this transaction
    val existingPayloadByTransactionId = syncQueryService.findByLegacyTransactionId(request.transactionId)
    if (existingPayloadByTransactionId != null) {
      val newBodyJson = objectMapper.writeValueAsString(request)

      val isBodyIdentical = jsonComparator.areJsonBodiesEqual(
        storedJson = existingPayloadByTransactionId.body,
        newJson = newBodyJson,
      )

      return if (isBodyIdentical) {
        TransactionSyncStatus.Duplicate(existingPayloadByTransactionId.synchronizedTransactionId)
      } else {
        TransactionSyncStatus.Updated(existingPayloadByTransactionId.synchronizedTransactionId)
      }
    }

    return TransactionSyncStatus.New
  }
}
