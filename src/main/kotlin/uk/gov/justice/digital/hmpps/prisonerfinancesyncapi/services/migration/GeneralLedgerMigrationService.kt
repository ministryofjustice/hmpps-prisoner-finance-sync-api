package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateStatementBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.LedgerAccountMappingService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.Instant

@Service("generalLedgerMigrationService")
class GeneralLedgerMigrationService(
  private val generalLedgerApiClient: GeneralLedgerApiClient,
  private val accountMapping: LedgerAccountMappingService,
  private val timeConversionService: TimeConversionService,
  private val telemetryClient: TelemetryClient,
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
    val balanceByAccount = mutableMapOf<String, BigDecimal>()
    val timestampByAccount = mutableMapOf<String, Instant>()

    request.accountBalances.forEach { balanceData ->

      val accountRef = accountMapping.mapPrisonerSubAccount(balanceData.accountCode)

      balanceByAccount[accountRef] = (
        balanceByAccount.getOrDefault(accountRef, BigDecimal.ZERO) + balanceData.balance
        )

      val asOfTimestamp = timeConversionService.toUtcInstant(balanceData.asOfTimestamp)
      timestampByAccount[accountRef] = (
        maxOf(
          timestampByAccount.getOrDefault(accountRef, asOfTimestamp),
          asOfTimestamp,
        )
        )
    }

    val parentAccount = generalLedgerApiClient.findAccountByReference(prisonNumber)
      ?: generalLedgerApiClient.createAccount(prisonNumber)

    for ((subAccountRef, balance) in balanceByAccount) {
      val subAccount = parentAccount.subAccounts.find { it.reference == subAccountRef }
        ?: generalLedgerApiClient.createSubAccount(parentAccount.id, subAccountRef)

      val request = CreateStatementBalanceRequest(
        balance.toPence(),
        timestampByAccount.getValue(subAccountRef),
      )

      val res = generalLedgerApiClient.migrateSubAccountBalance(subAccount.id, request)

      val logMessage = mapOf(
        "prisonNumber" to prisonNumber,
        "subAccountId" to res.subAccountId.toString(),
        "subAccountReference" to subAccountRef,
        "balance" to res.amount.toString(),
        "balanceDateTime" to res.balanceDateTime.toString(),
      )
      log.info("Successfully migrated balance $logMessage")
      telemetryClient.trackEvent(telemetryMigrationEvent, logMessage, null)
    }
  }
}
