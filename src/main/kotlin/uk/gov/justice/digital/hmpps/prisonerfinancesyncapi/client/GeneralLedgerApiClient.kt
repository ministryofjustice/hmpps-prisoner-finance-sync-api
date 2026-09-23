package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.AccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.SubAccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.TransactionControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateStatementBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PagedResponseSearchTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.StatementBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.TransactionResponse
import java.util.UUID
import kotlin.collections.firstOrNull

@Component
class GeneralLedgerApiClient(
  private val accountApi: AccountControllerApi,
  private val subAccountApi: SubAccountControllerApi,
  private val transactionApi: TransactionControllerApi,
) : ApiClientBase("General Ledger API") {

  // POST /sub-accounts/{subAccountId}/balance
  fun migrateSubAccountBalance(subAccountID: UUID, createStatementBalanceRequest: CreateStatementBalanceRequest): StatementBalanceResponse {
    val response = handleExceptions(
      block = {
        subAccountApi.postStatementBalance(subAccountID, createStatementBalanceRequest)
          .block()
      },
    )
    return response ?: throw IllegalStateException("Received null response when migrating sub-account $subAccountID")
  }

  // GET /accounts?reference={reference}
  fun findAccountByReference(reference: String): AccountResponse? = handleExceptions(
    { accountApi.getAccounts(reference).block() },
  )
    ?.firstOrNull()

  // GET /sub-accounts/{accountId}/balance
  fun findSubAccountBalanceByAccountId(accountId: UUID): SubAccountBalanceResponse? {
    val response = handleExceptions(
      block = {
        subAccountApi.getSubAccountBalance(accountId)
          .block()
      },
    )
    return response ?: throw IllegalStateException("Received null response when finding sub-account balance for $accountId")
  }

  // GET /sub-accounts?reference={subRef}&accountReference={parentRef}
  fun findSubAccount(parentReference: String, subAccountReference: String): SubAccountResponse? = subAccountApi.findSubAccounts(subAccountReference, parentReference)
    .block()
    ?.firstOrNull()

  // POST /accounts/{parentId}/sub-accounts
  fun createSubAccount(parentId: UUID, subAccountReference: String): SubAccountResponse {
    log.info("Creating Sub-Account $subAccountReference for Parent UUID $parentId")

    val request = CreateSubAccountRequest(subAccountReference = subAccountReference)

    return subAccountApi.createSubAccount(parentId, request)
      .block()
      ?: throw IllegalStateException("Received null response when creating sub-account $subAccountReference")
  }

  // POST /accounts
  fun createAccount(reference: String, type: CreateAccountRequest.Type): AccountResponse {
    log.info("Creating Account for ref: $reference")

    val request = CreateAccountRequest(accountReference = reference, type = type)

    return accountApi.createAccount(request)
      .block()
      ?: throw IllegalStateException("Received null response when creating account $reference")
  }

  // POST /transactions
  fun postTransaction(request: CreateTransactionRequest, idempotencyKey: UUID, transactionId: Long? = null): UUID {
    log.info("Posting transaction. NOMIS transactionId: $transactionId. NOMIS entrySequence ${request.entrySequence}. Key: $idempotencyKey")

    val response = transactionApi.postTransaction(idempotencyKey, request)
      .block()

    return response?.id
      ?: throw IllegalStateException("New GL API returned null body for transaction ${request.reference}")
  }

  // GET /transactions/transactionUUID
  fun getTransaction(transactionUUID: UUID): TransactionResponse? = transactionApi.getTransactionById(transactionUUID).block()

  // POST /transactions/search
  fun searchTransactions(glTransactionUUIDs: List<UUID>, pageNumber: Int, pageSize: Int): PagedResponseSearchTransactionResponse {
    var response = handleExceptions(
      { transactionApi.searchTransactions(glTransactionUUIDs, pageNumber = pageNumber, pageSize = pageSize).block() },
    )

    return response
      ?: throw IllegalStateException("GL Api returned null body for search transactions $response")
  }
}
