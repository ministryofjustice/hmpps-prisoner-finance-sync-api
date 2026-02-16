package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateStatementBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.StatementBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
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
  val telemetryMigrationEventError = "$telemetryMigrationEvent-error"

  private fun logMigration(req: CreateStatementBalanceRequest, res: StatementBalanceResponse?, prisonNumber: String, subAccountRef: String) {
    if (res == null) {
      val logMessage = mapOf(
        "prisonNumber" to prisonNumber,
        "subAccountReference" to subAccountRef,
        "balance" to req.amount.toString(),
        "balanceDateTime" to req.balanceDateTime.toString(),
      )

      log.error("Failed to migrate balance for prisoner $logMessage")
      telemetryClient.trackEvent(telemetryMigrationEventError, logMessage, null)
    } else {
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

  private fun tryMigrateSubAccountBalance(subAccount: SubAccountResponse, req: CreateStatementBalanceRequest, prisonNumber: String) {
    val res = try {
      generalLedgerApiClient.migrateSubAccountBalance(subAccount.id, req)
    } catch (e: WebClientResponseException) {
      log.error("HTTP response error", e)
      null
    }
    logMigration(req, res, prisonNumber, subAccount.reference)
  }

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

      tryMigrateSubAccountBalance(subAccount, request, prisonNumber)
    }
  }
}
