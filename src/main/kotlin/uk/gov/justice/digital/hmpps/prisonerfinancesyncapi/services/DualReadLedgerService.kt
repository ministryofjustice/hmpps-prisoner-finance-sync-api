package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService

@Primary
@Service
class DualReadLedgerService(
  @Qualifier("generalLedgerService") private val generalLedger: GeneralLedgerService,
  private val ledgerQueryService: LedgerQueryService,
  @Value("\${feature.general-ledger-api.enabled:false}") private val shouldSyncToGeneralLedger: Boolean,
  @Value("\${feature.general-ledger-api.test-prisoner-id:DISABLED}") private val testPrisonerId: String,
) : ReconciliationService {

  private companion object {
    private val log = LoggerFactory.getLogger(DualReadLedgerService::class.java)
  }

  init {
    log.info("General Ledger Dual Read Service initialized. Enabled: $shouldSyncToGeneralLedger. Test Prisoner ID: $testPrisonerId")
  }
  override fun reconcilePrisoner(prisonNumber: String): PrisonerEstablishmentBalanceDetailsList {
    val items = ledgerQueryService.listPrisonerBalancesByEstablishment(prisonNumber)

    if (prisonNumber == testPrisonerId) {
      try {
        generalLedger.reconcilePrisoner(prisonNumber)
      } catch (e: Exception) {
        log.error("Failed to reconcile prisoner $prisonNumber to General Ledger", e)
      }
    }

    return PrisonerEstablishmentBalanceDetailsList(items)
  }
}
