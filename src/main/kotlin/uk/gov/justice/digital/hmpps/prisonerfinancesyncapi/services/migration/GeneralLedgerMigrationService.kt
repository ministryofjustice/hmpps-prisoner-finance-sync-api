package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateStatementBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerAccountResolver
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.InMemoryAccountCache
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.Instant

@Service("generalLedgerMigrationService")
class GeneralLedgerMigrationService(
  private val generalLedgerApiClient: GeneralLedgerApiClient,
  private val timeConversionService: TimeConversionService,
  private val telemetryClient: TelemetryClient,
  private val accountResolver: GeneralLedgerAccountResolver,
) : MigrationService {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  override fun migrateGeneralLedgerBalances(
    prisonId: String,
    request: GeneralLedgerBalancesSyncRequest,
  ) {
    TODO("Not yet implemented")
  }

  val telemetryMigrationEvent = "prisoner-finance-sync-migration"

  override fun migratePrisonerBalances(
    prisonNumber: String,
    request: PrisonerBalancesSyncRequest,
  ) {
    val requestCache = InMemoryAccountCache()

    val balanceByAccount = mutableMapOf<Int, BigDecimal>()
    val timestampByAccount = mutableMapOf<Int, Instant>()

    request.accountBalances.forEach { balanceData ->
      balanceByAccount[balanceData.accountCode] = (
        balanceByAccount.getOrDefault(balanceData.accountCode, BigDecimal.ZERO) + balanceData.balance
        )

      val asOfTimestamp = timeConversionService.toUtcInstant(balanceData.asOfTimestamp)
      timestampByAccount[balanceData.accountCode] = (
        maxOf(
          timestampByAccount.getOrDefault(balanceData.accountCode, asOfTimestamp),
          asOfTimestamp,
        )
        )
    }

    for ((accountCode, balance) in balanceByAccount) {
      val subAccountId = accountResolver.resolveSubAccount(
        "",
        prisonNumber,
        accountCode,
        "",
        requestCache,
      )

      val request = CreateStatementBalanceRequest(
        balance.toPence(),
        timestampByAccount.getValue(accountCode),
      )

      val res = generalLedgerApiClient.migrateSubAccountBalance(subAccountId, request)

      val logMessage = mapOf(
        "prisonNumber" to prisonNumber,
        "subAccountId" to res.subAccountId.toString(),
        "AccountCode" to accountCode.toString(),
        "balance" to res.amount.toString(),
        "balanceDateTime" to res.balanceDateTime.toString(),
      )
      log.info("Successfully migrated balance $logMessage")
      telemetryClient.trackEvent(telemetryMigrationEvent, logMessage, null)
    }
  }
}
