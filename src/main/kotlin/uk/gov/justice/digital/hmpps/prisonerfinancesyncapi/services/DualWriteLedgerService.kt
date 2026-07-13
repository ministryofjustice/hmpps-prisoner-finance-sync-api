package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LegacyTransactionFixService
import java.util.UUID

@Primary
@Service
class DualWriteLedgerService(
  @Qualifier("internalLedgerService") private val internalLedger: LedgerService,
  @Qualifier("generalLedgerService") private val generalLedger: LedgerService,
  private val generalLedgerForwarder: GeneralLedgerForwarder,
  private val legacyTransactionFixService: LegacyTransactionFixService,
) : LedgerService {

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): List<UUID> {
    val fixedRequest = legacyTransactionFixService.fixLegacyTransactions(request)

    val result = internalLedger.syncOffenderTransaction(fixedRequest)
    val offenderDisplayId = request.offenderTransactions.firstOrNull()?.offenderDisplayId ?: ""

    generalLedgerForwarder.executeIfEnabled(
      "Failed to sync Offender Transaction ${request.transactionId} to General Ledger",
      offenderDisplayId,
      { generalLedger.syncOffenderTransaction(fixedRequest) },
    )

    return result
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = internalLedger.syncGeneralLedgerTransaction(request)
}
