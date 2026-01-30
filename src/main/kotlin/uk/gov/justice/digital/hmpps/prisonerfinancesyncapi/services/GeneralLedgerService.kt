package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlSubAccountResponse
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

  private fun getOrCreateAccount(reference: String): GlAccountResponse {
    var account = generalLedgerApiClient.findAccountByReference(reference)

    if (account != null) {
      log.info("General Ledger account found for '$reference' (UUID: ${account.id})")
      return account
    }

    log.info("General Ledger account not found for '$reference'. Creating new account.")
    account = generalLedgerApiClient.createAccount(reference)

    return account
  }

  private fun getOrCreateSubAccount(parentAccount: UUID, reference: String): GlSubAccountResponse {
    var subAccount = generalLedgerApiClient.findSubAccount(parentAccount, reference)
    if (subAccount != null) {
      log.info("General Ledger sub-account found for '$reference' (UUID: ${subAccount.id})")
      return subAccount
    }

    log.info("General Ledger sub-account not found for '$reference'. Creating new sub-account.")
    subAccount = generalLedgerApiClient.createSubAccount(parentAccount, reference)

    return subAccount
  }

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): UUID {
    val offenderId = request.offenderTransactions.first().offenderDisplayId

    val prisonAccount = getOrCreateAccount(request.caseloadId)

    // NB. this might need to be moved in the foreach block to handle multiple prisoners for transaction
    val prisonerAccount = getOrCreateAccount(offenderId)

    request.offenderTransactions.forEach { transaction ->
      transaction.generalLedgerEntries.forEach { entry ->

        val isPrisonerAccount = accountMapping.isValidPrisonerAccountCode(entry.code)

        val accountReference = if (isPrisonerAccount) {
          accountMapping.mapPrisonerSubAccount(entry.code)
        } else {
          accountMapping.mapPrisonSubAccount(
            entry.code,
            transaction.type,
          )
        }

        val parentAccount = if (isPrisonerAccount) prisonerAccount else prisonAccount
        val subAccount = getOrCreateSubAccount(parentAccount.id, accountReference)
      }
    }

    return UUID.randomUUID()
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = throw NotImplementedError("Syncing General Ledger Transactions is not yet supported in the new General Ledger Service")
}
