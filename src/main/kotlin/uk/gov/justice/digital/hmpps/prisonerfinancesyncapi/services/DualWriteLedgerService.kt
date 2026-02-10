package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.util.UUID

@Primary
@Service
class DualWriteLedgerService(
  @Qualifier("internalLedgerService") private val internalLedger: LedgerService,
  @Qualifier("generalLedgerService") private val generalLedger: LedgerService,
  @Value("\${feature.general-ledger-api.enabled:false}") private val shouldSyncToGeneralLedger: Boolean,
  @Value("\${feature.general-ledger-api.test-prisoner-id:DISABLED}") private val testPrisonerId: String,
) : LedgerService {

  private companion object {
    private val log = LoggerFactory.getLogger(DualWriteLedgerService::class.java)
  }

  init {
    log.info("General Ledger Dual Write Service initialized. Enabled: $shouldSyncToGeneralLedger. Test Prisoner ID: $testPrisonerId")
  }

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): UUID {
    val result = internalLedger.syncOffenderTransaction(request)

    if (shouldSyncToGeneralLedger) {
      val offenderDisplayId = request.offenderTransactions.firstOrNull()?.offenderDisplayId

      if (offenderDisplayId == testPrisonerId) {
        try {
          generalLedger.syncOffenderTransaction(request)
        } catch (e: WebClientResponseException) {
          log.error("Failed to sync Offender Transaction ${request.transactionId} to General Ledger. HTTP ${e.statusCode} - Body: ${e.responseBodyAsString}, ${e.request}", e)
        } catch (e: Exception) {
          log.error("Failed to sync Offender Transaction ${request.transactionId} to General Ledger", e)
        }
      }
    }

    return result
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = internalLedger.syncGeneralLedgerTransaction(request)
}
