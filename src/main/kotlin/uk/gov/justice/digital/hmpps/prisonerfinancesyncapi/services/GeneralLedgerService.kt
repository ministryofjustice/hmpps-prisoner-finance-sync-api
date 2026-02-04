package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlPostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlSubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.toGLPostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.util.UUID

@Service("generalLedgerService")
class GeneralLedgerService(
  private val generalLedgerApiClient: GeneralLedgerApiClient,
  private val accountMapping: LedgerAccountMappingService,
  private val timeConversionService: TimeConversionService,
) : LedgerService,
  ReconciliationService {

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

  private fun getOrCreateSubAccount(parentAccount: String, parentAccountId: UUID, reference: String): GlSubAccountResponse {
    var subAccount = generalLedgerApiClient.findSubAccount(parentAccount, reference)
    if (subAccount != null) {
      log.info("General Ledger sub-account found for '$reference' (UUID: ${subAccount.id})")
      return subAccount
    }

    log.info("General Ledger sub-account not found for '$reference'. Creating new sub-account.")
    subAccount = generalLedgerApiClient.createSubAccount(parentAccountId, reference)

    return subAccount
  }

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): UUID {
    val prisonAccount = getOrCreateAccount(request.caseloadId)
    val transactionGLUUIDs = mutableListOf<UUID>()

    val prisonerAccounts = mutableMapOf<String, GlAccountResponse>()

    request.offenderTransactions.forEach { transaction ->
      val offenderId = transaction.offenderDisplayId
      val prisonerAccount = prisonerAccounts.getOrPut(offenderId) {
        getOrCreateAccount(offenderId)
      }

      val glEntries = mutableListOf<GlPostingRequest>()

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

        val parentAccountString = if (isPrisonerAccount) offenderId else request.caseloadId
        val parentAccount = if (isPrisonerAccount) prisonerAccount else prisonAccount

        val subAccount = getOrCreateSubAccount(parentAccountString, parentAccount.id, accountReference)
        glEntries.add(
          GlPostingRequest(
            subAccount.id,
            type = entry.postingType.toGLPostingType(),
            amount = entry.amount.toPence(),
          ),
        )
      }

      val glTransactionRequest = GlTransactionRequest(
        transaction.reference ?: "",
        description = transaction.description,
        timestamp = timeConversionService.toUtcInstant(request.transactionTimestamp),
        amount = transaction.amount.toPence(),
        postings = glEntries,
      )

      // TODO: this should be the request.requestId once we have a transaction endpoint that supports multiple postings
      val transactionGLUUID = generalLedgerApiClient.postTransaction(glTransactionRequest, UUID.randomUUID())
      transactionGLUUIDs.add(transactionGLUUID)
    }

    if (transactionGLUUIDs.isEmpty()) {
      throw IllegalStateException("No General Ledger Transaction returned")
    }

    return transactionGLUUIDs.first()
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = throw NotImplementedError("Syncing General Ledger Transactions is not yet supported in the new General Ledger Service")

  override fun reconcilePrisoner(prisonNumber: String): PrisonerEstablishmentBalanceDetailsList = PrisonerEstablishmentBalanceDetailsList(emptyList())
}
