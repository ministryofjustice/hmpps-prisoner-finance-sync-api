package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateStatementBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.LedgerAccountMappingService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.LocalDateTime

@Service("generalLedgerMigrationService")
class GeneralLedgerMigrationService(
  private val generalLedgerApiClient: GeneralLedgerApiClient,
  private val accountMapping: LedgerAccountMappingService,
) : MigrationService {

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
    val balanceByAccount = mutableMapOf<String, BigDecimal>()
    val timestampByAccount = mutableMapOf<String, LocalDateTime>()

    request.accountBalances.forEach { balanceData ->

      val accountRef = accountMapping.mapPrisonerSubAccount(balanceData.accountCode)

      balanceByAccount[accountRef] = (
        balanceByAccount.getOrDefault(accountRef, BigDecimal.ZERO) + balanceData.balance
        )

      timestampByAccount[accountRef] = (
        maxOf(
          timestampByAccount.getOrDefault(accountRef, balanceData.asOfTimestamp),
          balanceData.asOfTimestamp,
        )
        )
    }

    val parentAccount = generalLedgerApiClient.findAccountByReference(prisonNumber)
      ?: generalLedgerApiClient.createAccount(prisonNumber)

    for ((subAccountRef, balance) in balanceByAccount) {
      val request = CreateStatementBalanceRequest(
        balance.toPence(),
        timestampByAccount.getValue(subAccountRef),
      )

      val subAccount = parentAccount.subAccounts.find { it.reference == subAccountRef }
        ?: generalLedgerApiClient.createSubAccount(parentAccount.id, subAccountRef)

      generalLedgerApiClient.migrateSubAccountBalance(subAccount.id, request)
    }
  }
}
