package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest

@Service("generalLedgerMigrationService")
class GeneralLedgerMigrationService : MigrationService {
  override fun migrateGeneralLedgerBalances(
    prisonId: String,
    request: GeneralLedgerBalancesSyncRequest,
  ) {
    TODO("Not yet implemented")
  }

  override fun migratePrisonerBalances(
    prisonNumber: String,
    request: PrisonerBalancesSyncRequest,
  ) {
    TODO("Not yet implemented")
  }
}
