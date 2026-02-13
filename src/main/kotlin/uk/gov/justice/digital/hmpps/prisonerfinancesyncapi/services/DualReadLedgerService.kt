package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService

@Primary
@Service
class DualReadLedgerService(
  @Qualifier("generalLedgerService") private val generalLedger: GeneralLedgerService,
  private val ledgerQueryService: LedgerQueryService,
  private val generalLedgerForwarder: GeneralLedgerForwarder,
) : ReconciliationService {

  override fun reconcilePrisoner(prisonNumber: String): PrisonerEstablishmentBalanceDetailsList {
    val items = ledgerQueryService.listPrisonerBalancesByEstablishment(prisonNumber)

    generalLedgerForwarder.executeIfEnabled<Unit>(
      "Failed to reconcile prisoner $prisonNumber to General Ledger",
      prisonNumber,
      { generalLedger.reconcilePrisoner(prisonNumber) },
    )

    return PrisonerEstablishmentBalanceDetailsList(items)
  }
}
