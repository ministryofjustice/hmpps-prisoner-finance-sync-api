package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.util.UUID

@Service("generalLedgerService")
class GeneralLedgerService(
  private val generalLedgerApiClient: GeneralLedgerApiClient,
) : LedgerService {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): UUID {
    val offenderId = request.offenderTransactions.first().offenderDisplayId

    val prisonerAccount = generalLedgerApiClient.findAccountByReference(offenderId)

    if (prisonerAccount != null) {
      log.info("General Ledger account found for '$offenderId' (UUID: ${prisonerAccount.id})")
    } else {
      generalLedgerApiClient.createAccount(offenderId)
      log.info("General Ledger account not found for '$offenderId'")
    }

    return UUID.randomUUID()
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = throw NotImplementedError("Syncing General Ledger Transactions is not yet supported in the new General Ledger Service")
}
