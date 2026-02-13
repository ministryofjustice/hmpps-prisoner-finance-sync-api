package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest

interface MigrationService {
  fun migrateGeneralLedgerBalances(prisonId: String, request: GeneralLedgerBalancesSyncRequest)
  fun migratePrisonerBalances(prisonNumber: String, request: PrisonerBalancesSyncRequest)
}
