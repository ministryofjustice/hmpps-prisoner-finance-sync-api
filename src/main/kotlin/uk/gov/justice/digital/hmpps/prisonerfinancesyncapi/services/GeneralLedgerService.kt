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
  private val accountMapping: LedgerAccountMappingService,
) : LedgerService {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  private fun getOrCreateAccount(reference: String): UUID {
    var account = generalLedgerApiClient.findAccountByReference(reference)

    if (account != null) {
      log.info("General Ledger account found for '$reference' (UUID: ${account.id})")
      return account.id
    }

    log.info("General Ledger account not found for '$reference'. Creating new account.")
    account = generalLedgerApiClient.createAccount(reference)

    return account.id
  }

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): UUID {
    val offenderId = request.offenderTransactions.first().offenderDisplayId

    val prisonAccount = getOrCreateAccount(request.caseloadId)
    val prisonerAccount = getOrCreateAccount(offenderId)

    request.offenderTransactions[0].generalLedgerEntries.forEach { entry ->

      if (accountMapping.isValidPrisonerAccountCode(entry.code)) {
        val subAccount = generalLedgerApiClient.findSubAccount(
          prisonerAccount.toString(),
          accountMapping.mapPrisonerSubAccount(entry.code),
        )
      } else {
        val subAccount = generalLedgerApiClient.findSubAccount(
          prisonerAccount.toString(),
          accountMapping.mapPrisonSubAccount(
            entry.code,
            request.offenderTransactions[0].type,
          ),
        )
      }
    }

    return UUID.randomUUID()
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = throw NotImplementedError("Syncing General Ledger Transactions is not yet supported in the new General Ledger Service")
}
