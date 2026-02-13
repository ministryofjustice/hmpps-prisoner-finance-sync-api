package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.AccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.SubAccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.TransactionControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateStatementBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import java.util.UUID
import kotlin.collections.firstOrNull

@Component
class GeneralLedgerApiClient(
  private val accountApi: AccountControllerApi,
  private val subAccountApi: SubAccountControllerApi,
  private val transactionApi: TransactionControllerApi,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  // POST /sub-accounts/{subAccountId}/balance
  fun migrateSubAccountBalance(subAccountID: UUID, createStatementBalanceRequest: CreateStatementBalanceRequest) = subAccountApi.postStatementBalance(subAccountID, createStatementBalanceRequest).block()

  // GET /accounts?reference={reference}
  fun findAccountByReference(reference: String): AccountResponse? = accountApi.getAccount(reference)
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
  fun createAccount(reference: String): AccountResponse {
    log.info("Creating Account for ref: $reference")

    val request = CreateAccountRequest(accountReference = reference)

    return accountApi.createAccount(request)
      .block()
      ?: throw IllegalStateException("Received null response when creating account $reference")
  }

  // POST /transactions
  fun postTransaction(request: CreateTransactionRequest, idempotencyKey: UUID): UUID {
    log.info("Posting transaction. Ref: ${request.reference}. Key: $idempotencyKey")

    val response = transactionApi.postTransaction(idempotencyKey, request)
      .block()

    return response?.id
      ?: throw IllegalStateException("New GL API returned null body for transaction ${request.reference}")
  }
}
