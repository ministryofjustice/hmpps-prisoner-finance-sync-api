package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.util.UUID

@Primary
@Service
class DualWriteLedgerService(
  @param:Qualifier("internalLedgerService") private val internalLedger: LedgerTransactionProcessor,
  @param:Qualifier("generalLedgerService") private val generalLedgerService: LedgerTransactionProcessor,
  @param:Value($$"${feature.general-ledger-api.enabled:false}") private val isNewGlEnabled: Boolean,
) : LedgerTransactionProcessor {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): UUID {
    val result = internalLedger.syncOffenderTransaction(request)

    if (isNewGlEnabled) {
      try {
        generalLedgerService.syncOffenderTransaction(request)
      } catch (e: Exception) {
        log.error("Failed to sync Offender Transaction ${request.transactionId} to New GL", e)
      }
    }

    return result
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID {
    val result = internalLedger.syncGeneralLedgerTransaction(request)

    if (isNewGlEnabled) {
      try {
        generalLedgerService.syncGeneralLedgerTransaction(request)
      } catch (e: Exception) {
        log.error("Failed to sync General Ledger Transaction ${request.transactionId} to New GL", e)
      }
    }

    return result
  }
}
