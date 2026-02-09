package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlSubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlSubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionRequest
import java.util.UUID

@Component
class GeneralLedgerApiClient(
  @Qualifier("generalLedgerApiWebClient") private val webClient: WebClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  // GET /accounts?reference={reference}
  fun findAccountByReference(reference: String): GlAccountResponse? {
    val responseList = webClient.get()
      .uri { uriBuilder ->
        uriBuilder.path("/accounts")
          .queryParam("reference", reference)
          .build()
      }
      .retrieve()
      .bodyToMono(object : ParameterizedTypeReference<List<GlAccountResponse>>() {})
      .block()

    return responseList?.firstOrNull()
  }

  // GET /sub-accounts/{accountId}/balance
  fun findSubAccountBalanceByAccountId(accountId: UUID): GlSubAccountBalanceResponse? {
    val response = webClient.get()
      .uri { uriBuilder ->
        uriBuilder.path("/sub-accounts/$accountId/balance")
          .build()
      }
      .retrieve()
      .bodyToMono(object : ParameterizedTypeReference<GlSubAccountBalanceResponse>() {})
      .block()

    return response
  }

  // GET /sub-accounts?reference={subRef}&accountReference={parentRef}
  fun findSubAccount(parentReference: String, subAccountReference: String): GlSubAccountResponse? {
    val responseList = webClient.get()
      .uri { uriBuilder ->
        uriBuilder.path("/sub-accounts")
          .queryParam("reference", subAccountReference)
          .queryParam("accountReference", parentReference)
          .build()
      }
      .retrieve()
      .bodyToMono(object : ParameterizedTypeReference<List<GlSubAccountResponse>>() {})
      .block()

    return responseList?.firstOrNull()
  }

  // POST /accounts/{parentId}/sub-accounts
  fun createSubAccount(parentId: UUID, subAccountReference: String): GlSubAccountResponse {
    log.info("Creating Sub-Account $subAccountReference for Parent UUID $parentId")

    val request = GlSubAccountRequest(subAccountReference)

    return webClient.post()
      .uri("/accounts/{parentId}/sub-accounts", parentId)
      .bodyValue(request)
      .retrieve()
      .bodyToMono(GlSubAccountResponse::class.java)
      .block()
      ?: throw IllegalStateException("Received null response when creating sub-account $subAccountReference")
  }

  fun createAccount(reference: String): GlAccountResponse {
    val request = GlAccountRequest(reference)

    log.info("Creating Account for ref: $reference")

    return webClient.post()
      .uri("/accounts")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(GlAccountResponse::class.java)
      .block()
      ?: throw IllegalStateException("Received null response when creating account $reference")
  }

  fun postTransaction(request: GlTransactionRequest, idempotencyKey: UUID): UUID {
    log.info("Posting transaction. Ref: ${request.reference}. Amount: ${request.amount}. Key: $idempotencyKey")

    val response = webClient.post()
      .uri("/transactions")
      .header("Idempotency-Key", idempotencyKey.toString())
      .bodyValue(request)
      .retrieve()
      .bodyToMono(GlTransactionReceipt::class.java)
      .block()

    return response?.id
      ?: throw IllegalStateException("New GL API returned null body for transaction ${request.reference}")
  }
}
