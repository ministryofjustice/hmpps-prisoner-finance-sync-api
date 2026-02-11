package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest

@Primary
@Service
class DualMigrationService(
  @Qualifier("legacyMigrationService") private val legacyMigrationService: MigrationService,
  @Qualifier("generalLedgerMigrationService") private val generalLedgerMigrationService: MigrationService,
  @Value("\${feature.general-ledger-api.enabled:false}") private val shouldSyncToGeneralLedger: Boolean,
  @Value("\${feature.general-ledger-api.test-prisoner-id:DISABLED}") private val testPrisonerId: String,
) : MigrationService {

  private companion object {
    private val log = LoggerFactory.getLogger(DualMigrationService::class.java)
  }

  init {
    log.info("General Ledger Dual Migration Service initialized. Enabled: $shouldSyncToGeneralLedger. Test Prisoner ID: $testPrisonerId")
  }

  override fun migratePrisonerBalances(prisonNumber: String, request: PrisonerBalancesSyncRequest) {
    legacyMigrationService.migratePrisonerBalances(prisonNumber, request)

    if (shouldSyncToGeneralLedger && prisonNumber == testPrisonerId) {
      try {
        generalLedgerMigrationService.migratePrisonerBalances(prisonNumber, request)
      } catch (e: Exception) {
        log.error("Failed to migrate prisoner balances for prisoner $prisonNumber to General Ledger", e)
      }
    }
  }

  override fun migrateGeneralLedgerBalances(prisonId: String, request: GeneralLedgerBalancesSyncRequest) {
    legacyMigrationService.migrateGeneralLedgerBalances(prisonId, request)
    if (shouldSyncToGeneralLedger && prisonId == testPrisonerId) {
      try {
        generalLedgerMigrationService.migrateGeneralLedgerBalances(prisonId, request)
      } catch (e: Exception) {
        log.error("Failed to migrate general ledger balances for prisoner $prisonId to General Ledger", e)
      }
    }
  }
}
