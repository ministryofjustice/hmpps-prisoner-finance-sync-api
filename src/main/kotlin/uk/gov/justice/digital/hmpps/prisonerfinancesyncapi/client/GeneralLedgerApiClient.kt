package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.AccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.SubAccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.TransactionControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.CustomException
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
) {

  private fun <T> handleExceptions(
    block: () -> T,
    message400: String = "Bad Request from General Ledger",
    message404: String = "Not found",
    message502: String = "Bad Gateway - General Ledger Unreachable or throwing an error",
    message500: String = "Unexpected Error",
  ): T {
    try {
      return block()
    } catch (e: WebClientResponseException) {
      when {
        e.statusCode == HttpStatus.BAD_REQUEST && e.responseBodyAsString.contains("Page requested is out of range") ->
          throw CustomException(message = "Page requested is out of range", status = HttpStatus.BAD_REQUEST)

        e.statusCode == HttpStatus.BAD_REQUEST -> throw CustomException(message400, HttpStatus.BAD_REQUEST, e)

        e.statusCode == HttpStatus.NOT_FOUND -> throw CustomException(message404, HttpStatus.NOT_FOUND, e)

        e.statusCode == HttpStatus.INTERNAL_SERVER_ERROR -> throw CustomException(message502, HttpStatus.BAD_GATEWAY, e)

        else -> throw CustomException(message500, HttpStatus.INTERNAL_SERVER_ERROR, e)
      }
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

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
  fun findAccountByReference(reference: String): AccountResponse? = accountApi.getAccounts(reference)
    .block()
    ?.firstOrNull()

  // GET /sub-accounts/{accountId}/balance
  fun findSubAccountBalanceByAccountId(accountId: UUID): SubAccountBalanceResponse? = subAccountApi.getSubAccountBalance(accountId)
    .block()

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
