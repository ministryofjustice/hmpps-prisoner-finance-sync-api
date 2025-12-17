package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import java.util.UUID

@Service
class SyncService(
  private val ledgerSyncService: LedgerTransactionProcessor,
  private val requestCaptureService: RequestCaptureService,
  private val syncQueryService: SyncQueryService,
  private val jsonComparator: JsonComparator,
  private val objectMapper: ObjectMapper,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun <T : SyncRequest> syncTransaction(
    request: T,
  ): SyncTransactionReceipt {
    val existingPayloadByRequestId = syncQueryService.findByRequestId(request.requestId)
    if (existingPayloadByRequestId != null) {
      return SyncTransactionReceipt(
        requestId = request.requestId,
        synchronizedTransactionId = existingPayloadByRequestId.synchronizedTransactionId,
        action = SyncTransactionReceipt.Action.PROCESSED,
      )
    }

    val existingPayloadByTransactionId = syncQueryService.findByLegacyTransactionId(request.transactionId)
    if (existingPayloadByTransactionId != null) {
      val newBodyJson = try {
        objectMapper.writeValueAsString(request)
      } catch (e: Exception) {
        log.error("Could not serialize new request body to JSON", e)
        "{}"
      }

      val isBodyIdentical = jsonComparator.areJsonBodiesEqual(
        storedJson = existingPayloadByTransactionId.body,
        newJson = newBodyJson,
      )

      if (isBodyIdentical) {
        return SyncTransactionReceipt(
          requestId = request.requestId,
          synchronizedTransactionId = existingPayloadByTransactionId.synchronizedTransactionId,
          action = SyncTransactionReceipt.Action.PROCESSED,
        )
      } else {
        val newPayload = requestCaptureService.captureAndStoreRequest(
          request,
          existingPayloadByTransactionId.synchronizedTransactionId,
        )
        return SyncTransactionReceipt(
          requestId = request.requestId,
          synchronizedTransactionId = newPayload.synchronizedTransactionId,
          action = SyncTransactionReceipt.Action.UPDATED,
        )
      }
    }

    val synchronizedTransactionId: UUID = try {
      processLedgerRequest(request)
    } catch (e: DataIntegrityViolationException) {
      log.warn("Race condition detected for transactionId: ${request.transactionId}. Retrying operation...")
      try {
        processLedgerRequest(request)
      } catch (retryEx: Exception) {
        logRequestAsError(request, retryEx)
        throw retryEx
      }
    } catch (e: Exception) {
      logRequestAsError(request, e)
      throw e
    }

    val newPayload = requestCaptureService.captureAndStoreRequest(request, synchronizedTransactionId)
    return SyncTransactionReceipt(
      requestId = request.requestId,
      synchronizedTransactionId = newPayload.synchronizedTransactionId,
      action = SyncTransactionReceipt.Action.CREATED,
    )
  }

  private fun <T : SyncRequest> processLedgerRequest(request: T): UUID = when (request) {
    is SyncOffenderTransactionRequest -> {
      ledgerSyncService.syncOffenderTransaction(request)
    }
    is SyncGeneralLedgerTransactionRequest -> {
      ledgerSyncService.syncGeneralLedgerTransaction(request)
    }
    else -> throw IllegalArgumentException("Unknown request type: ${request::class.java.simpleName}")
  }

  private fun logRequestAsError(request: SyncRequest, exception: Exception) {
    val requestJson = try {
      objectMapper.writeValueAsString(request)
    } catch (e: Exception) {
      "Could not serialize request body to JSON for error logging: ${e.message}"
    }
    log.error("Error processing sync transaction with requestId: ${request.requestId}, transactionId: ${request.transactionId}. Request body: $requestJson", exception)
  }
}
