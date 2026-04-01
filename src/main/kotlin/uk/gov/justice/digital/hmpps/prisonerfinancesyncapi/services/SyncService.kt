package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.TransactionSyncStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync.SyncPayloadCaptureService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync.SyncStatusResolver
import java.util.UUID

@Service
class SyncService(
  private val ledgerSyncService: LedgerService,
  private val syncPayloadCaptureService: SyncPayloadCaptureService,
  private val syncStatusResolver: SyncStatusResolver,
  private val telemetryClient: TelemetryClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun <T : SyncRequest> syncTransaction(
    request: T,
  ): SyncTransactionReceipt {
    val status = syncStatusResolver.check(request)

    return when (status) {
      is TransactionSyncStatus.Duplicate -> {
        SyncTransactionReceipt(
          requestId = request.requestId,
          synchronizedTransactionId = status.synchronizedTransactionId,
          action = SyncTransactionReceipt.Action.PROCESSED,
        )
      }

      is TransactionSyncStatus.Updated -> {
        val newPayload = syncPayloadCaptureService.captureAndStoreRequest(request, status.synchronizedTransactionId)
        SyncTransactionReceipt(
          requestId = request.requestId,
          synchronizedTransactionId = newPayload.synchronizedTransactionId,
          action = SyncTransactionReceipt.Action.UPDATED,
        )
      }

      is TransactionSyncStatus.New -> {
        processNewTransaction(request)
      }
    }
  }

  private fun processNewTransaction(request: SyncRequest): SyncTransactionReceipt {
    val synchronizedTransactionId: UUID = try {
      processLedgerRequest(request)
    } catch (_: DataIntegrityViolationException) {
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

    val newPayload = syncPayloadCaptureService.captureAndStoreRequest(request, synchronizedTransactionId)
    return SyncTransactionReceipt(
      requestId = request.requestId,
      synchronizedTransactionId = newPayload.synchronizedTransactionId,
      action = SyncTransactionReceipt.Action.CREATED,
    )
  }

  private fun processLedgerRequest(request: SyncRequest): UUID = when (request) {
    is SyncOffenderTransactionRequest -> {
      ledgerSyncService.syncOffenderTransaction(request)
        .firstOrNull()
        ?: throw IllegalStateException("No transaction ID returned for offender sync")
    }
    is SyncGeneralLedgerTransactionRequest -> {
      ledgerSyncService.syncGeneralLedgerTransaction(request)
    }
    else -> throw IllegalArgumentException("Unknown request type: ${request::class.java.simpleName}")
  }

  private fun logRequestAsError(request: SyncRequest, exception: Exception) {
    val properties = mutableMapOf(
      "requestId" to request.requestId.toString(),
      "transactionId" to request.transactionId.toString(),
      "requestType" to (request::class.simpleName ?: "UnknownRequest"),
    )

    val transactionType = when (request) {
      is SyncOffenderTransactionRequest -> request.offenderTransactions.firstOrNull()?.type
      is SyncGeneralLedgerTransactionRequest -> request.transactionType
      else -> null
    }

    if (!transactionType.isNullOrBlank()) {
      properties["transactionType"] = transactionType
    }

    log.error("Error processing sync transaction: $properties", exception)

    telemetryClient.trackException(exception, properties, null)
  }
}
