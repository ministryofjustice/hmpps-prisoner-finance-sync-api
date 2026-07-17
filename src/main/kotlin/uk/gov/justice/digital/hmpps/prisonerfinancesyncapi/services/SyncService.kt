package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.TransactionSyncStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LegacyTransactionFixService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync.SyncPayloadCaptureService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync.SyncStatusResolver
import java.util.UUID

@Service
class SyncService(
  private val ledgerSyncService: LedgerService,
  private val syncPayloadCaptureService: SyncPayloadCaptureService,
  private val syncStatusResolver: SyncStatusResolver,
  private val legacyTransactionFixService: LegacyTransactionFixService,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun <T : SyncRequest> syncTransaction(request: T): SyncTransactionReceipt = when (val status = syncStatusResolver.check(request)) {
    is TransactionSyncStatus.Duplicate -> {
      processDuplicateTransactionRequest(status, request)
    }

    is TransactionSyncStatus.Updated -> {
      processUpdatedTransactionRequest(status, request)
    }

    is TransactionSyncStatus.New -> {
      processNewTransactionRequest(request)
    }
  }

  private fun <T : SyncRequest> processNewTransactionRequest(request: T): SyncTransactionReceipt {
    val newSynchronizedTransactionId = UUID.randomUUID()
    val newPayload = syncPayloadCaptureService.captureAndStoreRequest(request, newSynchronizedTransactionId)

    try {
      processNewLedgerRequestWithRetry(request)
      syncPayloadCaptureService.updatePayloadStatus(request, NomisSyncPayload.Status.PROCESSED)

      val receipt = SyncTransactionReceipt(
        requestId = newPayload.requestId,
        transactionId = newPayload.legacyTransactionId,
        synchronizedTransactionId = newPayload.synchronizedTransactionId,
        action = SyncTransactionReceipt.Action.CREATED,
      )

      log.info("Sync transaction: New transaction request received $receipt")

      return receipt
    } catch (unexpectedException: Exception) {
      syncPayloadCaptureService.updatePayloadStatus(request, NomisSyncPayload.Status.FAILED)

      val receipt = SyncTransactionReceipt(
        requestId = newPayload.requestId,
        transactionId = newPayload.legacyTransactionId,
        synchronizedTransactionId = newPayload.synchronizedTransactionId,
        action = SyncTransactionReceipt.Action.PROCESSED_WITH_ERRORS,
      )

      log.error("Sync transaction: New transaction request received causing errors $receipt", unexpectedException)

      throw unexpectedException
    }
  }

  private fun <T : SyncRequest> processDuplicateTransactionRequest(status: TransactionSyncStatus.Duplicate, request: T): SyncTransactionReceipt {
    syncPayloadCaptureService.updatePayloadStatus(request, NomisSyncPayload.Status.PROCESSED)

    val receipt = SyncTransactionReceipt(
      requestId = request.requestId,
      transactionId = request.transactionId,
      synchronizedTransactionId = status.synchronizedTransactionId,
      action = SyncTransactionReceipt.Action.PROCESSED,
    )

    log.info("Sync transaction: Duplicate transaction request received $receipt")

    return receipt
  }

  private fun <T : SyncRequest> processUpdatedTransactionRequest(status: TransactionSyncStatus.Updated, request: T): SyncTransactionReceipt {
    val newPayload = syncPayloadCaptureService.captureAndStoreRequest(request, status.synchronizedTransactionId)
    syncPayloadCaptureService.updatePayloadStatus(request, NomisSyncPayload.Status.PROCESSED)

    val receipt = SyncTransactionReceipt(
      requestId = newPayload.requestId,
      transactionId = newPayload.legacyTransactionId,
      synchronizedTransactionId = newPayload.synchronizedTransactionId,
      action = SyncTransactionReceipt.Action.UPDATED,
    )

    log.info("Sync transaction: Updated transaction request received $receipt")

    return receipt
  }

  private fun <T : SyncRequest> processNewLedgerRequestWithRetry(request: T): UUID {
    try {
      return processNewLedgerRequest(request)
    } catch (_: DataIntegrityViolationException) {
      log.warn("Race condition detected for transactionId: ${request.transactionId}. Retrying operation...")
      return processNewLedgerRequest(request) // throw anything we get the second time
    }
  }

  private fun processNewLedgerRequest(request: SyncRequest): UUID = when (request) {
    is SyncOffenderTransactionRequest -> {
      val fixedRequest = legacyTransactionFixService.fixLegacyTransactions(request)

      ledgerSyncService.syncOffenderTransaction(fixedRequest)
        .firstOrNull()
        ?: throw IllegalStateException("No transaction ID returned for transactionId: ${request.transactionId}")
    }

    is SyncGeneralLedgerTransactionRequest -> {
      ledgerSyncService.syncGeneralLedgerTransaction(request)
    }

    else -> throw IllegalArgumentException("Unknown request type: ${request::class.java.simpleName}")
  }
}
