package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException

@Service
class GeneralLedgerForwarder(
  @Value("\${feature.general-ledger-api.enabled:false}") private val shouldSyncToGeneralLedger: Boolean,
  @Value("\${feature.general-ledger-api.test-prisoner-ids:DISABLED}") private val testPrisonerIds: List<String>,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
  init {
    log.info("GeneralLedgerSwitchManager initialized. Enabled: $shouldSyncToGeneralLedger. Test Prisoner IDs: $testPrisonerIds")
  }

  fun <T> executeIfEnabled(
    logErrorMessage: String,
    prisonNumber: String,
    block: () -> T,
  ): T? {
    if (shouldSyncToGeneralLedger && testPrisonerIds.contains(prisonNumber)) {
      try {
        return block()
      } catch (e: WebClientResponseException) {
        log.error("HTTP Error to General Ledger. HTTP ${e.statusCode} - Body: ${e.responseBodyAsString}, ${e.request}", e)
      } catch (e: Exception) {
        log.error(logErrorMessage, e)
      }
    }

    return null
  }
}
