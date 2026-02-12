package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerSwitchManager

@Primary
@Service
class DualMigrationService(
  @Qualifier("legacyMigrationService") private val legacyMigrationService: MigrationService,
  @Qualifier("generalLedgerMigrationService") private val generalLedgerMigrationService: MigrationService,
  private val generalLedgerSwitchManager: GeneralLedgerSwitchManager,
) : MigrationService {

  override fun migratePrisonerBalances(prisonNumber: String, request: PrisonerBalancesSyncRequest) {
    legacyMigrationService.migratePrisonerBalances(prisonNumber, request)

    generalLedgerSwitchManager.forwardToGeneralLedgerIfEnabled(
      "Failed to migrate prisoner balances for prisoner $prisonNumber to General Ledger",
      prisonNumber,
      { generalLedgerMigrationService.migratePrisonerBalances(prisonNumber, request) },
    )
  }

  override fun migrateGeneralLedgerBalances(prisonId: String, request: GeneralLedgerBalancesSyncRequest) {
    legacyMigrationService.migrateGeneralLedgerBalances(prisonId, request)

    generalLedgerSwitchManager.forwardToGeneralLedgerIfEnabled(
      "Failed to migrate general ledger balances for prisoner $prisonId to General Ledger",
      prisonId,
      {
        generalLedgerMigrationService.migrateGeneralLedgerBalances(prisonId, request)
      },
    )
  }
}
