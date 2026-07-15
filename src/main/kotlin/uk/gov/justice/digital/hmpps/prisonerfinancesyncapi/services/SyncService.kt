package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
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

  fun <T : SyncRequest> syncTransaction(
    request: T,
  ): SyncTransactionReceipt = when (val status = syncStatusResolver.check(request)) {
    is TransactionSyncStatus.Duplicate -> {
      log.info(
        """
        Duplicate transaction request received 
        { 
          requestId: ${request.requestId}, 
          transactionId: ${request.transactionId}, 
          action: ${SyncTransactionReceipt.Action.PROCESSED}, 
          synchronizedTransactionId: ${status.synchronizedTransactionId}
        }
        """,
      )
      SyncTransactionReceipt(
        requestId = request.requestId,
        synchronizedTransactionId = status.synchronizedTransactionId,
        action = SyncTransactionReceipt.Action.PROCESSED,
      )
    }

    is TransactionSyncStatus.Updated -> {
      val newPayload = syncPayloadCaptureService.captureAndStoreRequest(request, status.synchronizedTransactionId)
      log.info(
        """
        Updated transaction request received 
        { 
          requestId: ${request.requestId}, 
          transactionId: ${request.transactionId}, 
          action: ${SyncTransactionReceipt.Action.PROCESSED}, 
          synchronizedTransactionId: ${newPayload.synchronizedTransactionId}
        }
        """,
      )
      SyncTransactionReceipt(
        requestId = request.requestId,
        synchronizedTransactionId = newPayload.synchronizedTransactionId,
        action = SyncTransactionReceipt.Action.UPDATED,
      )
    }

    is TransactionSyncStatus.New -> {
      val synchronisedTransactionId = UUID.randomUUID()
      val newPayload = syncPayloadCaptureService.captureAndStoreRequest(request, synchronisedTransactionId)
      processNewTransaction(request)
      log.info(
        """
        Duplicate transaction request received 
        { 
          requestId: ${request.requestId}, 
          transactionId: ${request.transactionId}, 
          action: ${SyncTransactionReceipt.Action.PROCESSED}, 
          synchronizedTransactionId: $synchronisedTransactionId
        }
        """,
      )
      SyncTransactionReceipt(
        requestId = request.requestId,
        synchronizedTransactionId = newPayload.synchronizedTransactionId,
        action = SyncTransactionReceipt.Action.CREATED,
      )
    }
  }

  private fun processNewTransaction(request: SyncRequest): UUID {
    return try {
      processLedgerRequest(request)
    } catch (_: DataIntegrityViolationException) {
      log.warn("Race condition detected for transactionId: ${request.transactionId}. Retrying operation...")
      null
    } catch (firstTryEx: Exception) {
      throw firstTryEx
    } ?: processLedgerRequest(request) // throw anything we get the second time
  }

  private fun processLedgerRequest(request: SyncRequest): UUID = when (request) {
    is SyncOffenderTransactionRequest -> {
      val fixedRequest = legacyTransactionFixService.fixLegacyTransactions(request)
      ledgerSyncService.syncOffenderTransaction(fixedRequest)
        .firstOrNull()
        ?: throw IllegalStateException("No transaction ID returned for offender sync")
    }

    is SyncGeneralLedgerTransactionRequest -> {
      ledgerSyncService.syncGeneralLedgerTransaction(request)
    }

    else -> throw IllegalArgumentException("Unknown request type: ${request::class.java.simpleName}")
  }
}
