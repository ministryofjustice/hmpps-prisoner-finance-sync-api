package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GeneralLedgerDiscrepancyDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.util.UUID
import kotlin.math.abs

@Service("generalLedgerService")
class GeneralLedgerService(
  private val generalLedgerApiClient: GeneralLedgerApiClient,
  private val accountMapping: LedgerAccountMappingService,
  private val ledgerQueryService: LedgerQueryService,
  private val telemetryClient: TelemetryClient,
  private val timeConversionService: TimeConversionService,
) : LedgerService,
  ReconciliationService {

  private companion object {
    private val log = LoggerFactory.getLogger(GeneralLedgerService::class.java)
  }

  private fun getOrCreateAccount(reference: String): AccountResponse {
    var account = generalLedgerApiClient.findAccountByReference(reference)

    if (account != null) {
      log.info("General Ledger account found for '$reference' (UUID: ${account.id})")
      return account
    }

    log.info("General Ledger account not found for '$reference'. Creating new account.")
    account = generalLedgerApiClient.createAccount(reference)

    return account
  }

  private fun getOrCreateSubAccount(parentAccount: String, parentAccountId: UUID, reference: String): SubAccountResponse {
    var subAccount = generalLedgerApiClient.findSubAccount(parentAccount, reference)
    if (subAccount != null) {
      log.info("General Ledger sub-account found for '$reference' (UUID: ${subAccount.id})")
      return subAccount
    }

    log.info("General Ledger sub-account not found for '$reference'. Creating new sub-account.")
    subAccount = generalLedgerApiClient.createSubAccount(parentAccountId, reference)

    return subAccount
  }

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): List<UUID> {
    val prisonAccount = getOrCreateAccount(request.caseloadId)
    val transactionGLUUIDs = mutableListOf<UUID>()

    val prisonerAccounts = mutableMapOf<String, AccountResponse>()

    request.offenderTransactions.forEach { transaction ->
      val offenderId = transaction.offenderDisplayId
      val prisonerAccount = prisonerAccounts.getOrPut(offenderId) {
        getOrCreateAccount(offenderId)
      }

      val glEntries = mutableListOf<CreatePostingRequest>()

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
          CreatePostingRequest(
            subAccountId = subAccount.id,
            type = CreatePostingRequest.Type.valueOf(entry.postingType),
            amount = entry.amount.toPence(),
          ),
        )
      }

      val glTransactionRequest = CreateTransactionRequest(
        reference = transaction.reference ?: "",
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

    return transactionGLUUIDs
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = throw NotImplementedError("Syncing General Ledger Transactions is not yet supported in the new General Ledger Service")

  private fun getGLPrisonerBalances(prisonNumber: String): Map<String, SubAccountBalanceResponse> {
    val parentAccount = generalLedgerApiClient.findAccountByReference(prisonNumber)

    if (parentAccount == null) {
      log.error("No parent account found for prisoner $prisonNumber")
      return emptyMap()
    }

    if (parentAccount.subAccounts.isEmpty()) {
      log.error("No sub accounts found for prisoner $prisonNumber")
      return emptyMap()
    }

    val subAccounts = mutableMapOf<String, SubAccountBalanceResponse>()
    for (account in parentAccount.subAccounts) {
      val subAccountBalance = generalLedgerApiClient.findSubAccountBalanceByAccountId(account.id)
      if (subAccountBalance == null) {
        log.error("No balance found for account ${account.id} but it was in the parent subaccounts list")
        continue
      }
      subAccounts[account.reference] = subAccountBalance
    }

    return subAccounts
  }

  override fun reconcilePrisoner(prisonNumber: String): PrisonerEstablishmentBalanceDetailsList {
    val subAccountsGL = getGLPrisonerBalances(prisonNumber)

    val legacyBalancesByEstablishment = ledgerQueryService.listPrisonerBalancesByEstablishment(prisonNumber)

    for (accountCode in accountMapping.prisonerSubAccounts.keys) {
      val legacyBalance = ledgerQueryService.aggregatedLegacyBalanceForAccountCode(
        accountMapping.mapSubAccountPrisonerReferenceToNOMIS(accountCode),
        legacyBalancesByEstablishment,
      )

      val glAccount = subAccountsGL[accountCode]
      if (glAccount == null || legacyBalance != glAccount.amount) {
        var message = "Discrepancy found for prisoner $prisonNumber"
        if (glAccount == null) message = "Gl account not found for prisoner $prisonNumber"

        val errorDetails = GeneralLedgerDiscrepancyDetails(
          message = message,
          prisonerId = prisonNumber,
          accountType = accountCode,
          legacyAggregatedBalance = legacyBalance,
          generalLedgerBalance = glAccount?.amount ?: 0,
          discrepancy = abs(legacyBalance - (glAccount?.amount ?: 0)),
          glBreakdown = subAccountsGL.values.toList(),
          legacyBreakdown = legacyBalancesByEstablishment,
        )
        log.warn("{}", errorDetails)

        telemetryClient.trackEvent(
          "prisoner-finance-sync-reconciliation-discrepancy-with-general-ledger",
          mapOf(
            "message" to errorDetails.message,
            "prisonerId" to errorDetails.prisonerId,
            "legacyAggregatedBalance" to errorDetails.legacyAggregatedBalance.toString(),
            "generalLedgerBalance" to errorDetails.generalLedgerBalance.toString(),
            "discrepancy" to errorDetails.discrepancy.toString(),
            "glBreakdown" to errorDetails.glBreakdown.toString(),
            "legacyBreakdown" to errorDetails.legacyBreakdown.toString(),
          ),
          null,
        )
      }
    }

    return PrisonerEstablishmentBalanceDetailsList(legacyBalancesByEstablishment)
  }
}
