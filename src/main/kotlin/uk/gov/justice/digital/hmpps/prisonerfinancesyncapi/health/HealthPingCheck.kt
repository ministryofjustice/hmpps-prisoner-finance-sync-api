@file:Suppress("ktlint:standard:filename")

package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.health.HealthPingCheck

@Component("hmppsAuth")
class HmppsAuthHealthPing(@Qualifier("hmppsAuthHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("generalLedgerApi")
class GeneralLedgerApiHealthPing(@Qualifier("generalLedgerHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)
