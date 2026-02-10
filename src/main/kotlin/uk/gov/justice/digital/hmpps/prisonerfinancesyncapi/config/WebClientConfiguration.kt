package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.AccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.SubAccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.TransactionControllerApi
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @param:Value("\${hmpps-auth.url}") val hmppsAuthBaseUri: String,
  @Value("\${general-ledger-api.url}") private val generalLedgerApiBaseUri: String,
  @param:Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @param:Value("\${api.timeout:20s}") val timeout: Duration,
) {
  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)

  @Bean
  fun generalLedgerHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(generalLedgerApiBaseUri, healthTimeout)

  @Bean
  fun generalLedgerApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    registrationId = "general-ledger-api",
    url = generalLedgerApiBaseUri,
    timeout = timeout,
  )

  @Bean
  fun accountApi(@Qualifier("generalLedgerApiWebClient") webClient: WebClient): AccountControllerApi = AccountControllerApi(webClient)

  @Bean
  fun subAccountApi(@Qualifier("generalLedgerApiWebClient") webClient: WebClient): SubAccountControllerApi = SubAccountControllerApi(webClient)

  @Bean
  fun transactionApi(@Qualifier("generalLedgerApiWebClient") webClient: WebClient): TransactionControllerApi = TransactionControllerApi(webClient)
}
