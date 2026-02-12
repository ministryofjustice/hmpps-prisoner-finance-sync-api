package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.util.UUID

@Primary
@Service
class DualWriteLedgerService(
  @Qualifier("internalLedgerService") private val internalLedger: LedgerService,
  @Qualifier("generalLedgerService") private val generalLedger: LedgerService,
  private val generalLedgerSwitchManager: GeneralLedgerSwitchManager,
) : LedgerService {

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): UUID {
    val result = internalLedger.syncOffenderTransaction(request)
    val offenderDisplayId = request.offenderTransactions.firstOrNull()?.offenderDisplayId ?: ""

    generalLedgerSwitchManager.forwardToGeneralLedgerIfEnabled(
      "Failed to sync Offender Transaction ${request.transactionId} to General Ledger",
      offenderDisplayId,
      { generalLedger.syncOffenderTransaction(request) },
    )

    return result
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = internalLedger.syncGeneralLedgerTransaction(request)
}
