package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionRequest
import java.util.UUID

@Component
class GeneralLedgerApiClient(
  @param:Qualifier("generalLedgerApiWebClient") private val webClient: WebClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun findAccountByReference(reference: String): GlAccountResponse? {
    return try {
      webClient.get()
        .uri { uriBuilder ->
          uriBuilder.path("/accounts")
            .queryParam("reference", reference)
            .build()
        }
        .retrieve()
        .bodyToMono(GlAccountResponse::class.java)
        .block()
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.NOT_FOUND) {
        return null
      }
      throw e
    }
  }

  fun createAccount(name: String, reference: String): GlAccountResponse {
    val request = GlAccountRequest(name, reference)

    log.info("Creating Account for ref: $reference")

    return try {
      webClient.post()
        .uri("/accounts")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(GlAccountResponse::class.java)
        .block()!!
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.CONFLICT) {
        log.info("Race condition: Account $reference created by another process. Fetching it now.")
        return findAccountByReference(reference)
          ?: throw IllegalStateException("Account reported as existing (409) but could not be found")
      }
      throw e
    }
  }

  fun postTransaction(request: GlTransactionRequest): UUID {
    log.info("Posting transaction. Ref: ${request.reference}. Dr: ${request.debtorAccount}, Cr: ${request.creditorAccount}, Amount: ${request.amount}")

    val response = webClient.post()
      .uri("/transactions")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(GlTransactionReceipt::class.java)
      .block()

    return response?.id
      ?: throw IllegalStateException("New GL API returned null body for transaction ${request.reference}")
  }
}
