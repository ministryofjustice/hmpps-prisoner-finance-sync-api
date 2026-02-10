
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.DualReadLedgerService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration.MigrationService

@Primary
@Service
class DualMigrationService(
  @Qualifier("legacyMigrationService") private val migrationService: MigrationService,
  @Qualifier("generalLedgerMigrationService") private val generalLedgerMigrationService: MigrationService,
  @Value("\${feature.general-ledger-api.enabled:false}") private val shouldSyncToGeneralLedger: Boolean,
  @Value("\${feature.general-ledger-api.test-prisoner-id:DISABLED}") private val testPrisonerId: String,
) : MigrationService {

  private companion object {
    private val log = LoggerFactory.getLogger(DualReadLedgerService::class.java)
  }

  init {
    log.info("General Ledger Dual Migration Service initialized. Enabled: $shouldSyncToGeneralLedger. Test Prisoner ID: $testPrisonerId")
  }

  override fun migratePrisonerBalances(prisonNumber: String, request: PrisonerBalancesSyncRequest) {
    migrationService.migratePrisonerBalances(prisonNumber, request)

    if (shouldSyncToGeneralLedger && prisonNumber == testPrisonerId) {
      generalLedgerMigrationService.migratePrisonerBalances(prisonNumber, request)
    }
  }

  override fun migrateGeneralLedgerBalances(prisonId: String, request: GeneralLedgerBalancesSyncRequest) {
    migrationService.migrateGeneralLedgerBalances(prisonId, request)
    if (shouldSyncToGeneralLedger && prisonId == testPrisonerId) {
      generalLedgerMigrationService.migrateGeneralLedgerBalances(prisonId, request)
    }
  }
}
