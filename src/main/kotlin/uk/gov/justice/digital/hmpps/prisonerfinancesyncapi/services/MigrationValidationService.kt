package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.exceptions.GeneralLedgerAccountNotFoundException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GeneralLedgerDiscrepancyDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import kotlin.math.abs

@Service
class MigrationValidationService(
  private val generalLedgerService: GeneralLedgerService,
  private val telemetryClient: TelemetryClient,
) {

  fun createDiscrepancyReport(
    prisonNumber: String,
    subAccountRef: String,
    generalLedgerBalance: SubAccountBalanceResponse?,
    nomisBalances: List<PrisonerAccountPointInTimeBalance>,
    glBalanceAmount: Long,
    nomisBalanceAmount: Long,
  ): GeneralLedgerDiscrepancyDetails = GeneralLedgerDiscrepancyDetails(
    message = "NOMIS balances do not match with general ledger balances",
    prisonerId = prisonNumber,
    accountType = subAccountRef,
    legacyAggregatedBalance = nomisBalanceAmount,
    generalLedgerBalance = glBalanceAmount,
    discrepancy = abs(nomisBalanceAmount - glBalanceAmount),
    glBreakdown = if (generalLedgerBalance != null) listOf(generalLedgerBalance) else emptyList(),
    legacyBreakdown = nomisBalances.map { it.toPrisonerEstablishmentBalanceDetails() },
  )

  private companion object {
    private val log = LoggerFactory.getLogger(MigrationValidationService::class.java)
  }

  val validationMismatchEventName: String = "prisoner-finance-sync-api-balance-validation-mismatch"

  val prisonerSubAccounts = mapOf("CASH" to 2101, "SPENDS" to 2102, "SAVINGS" to 2103)

  fun validatePrisonerBalances(prisonNumber: String, accountBalances: List<PrisonerAccountPointInTimeBalance>): List<GeneralLedgerDiscrepancyDetails> {
    val aggregatedBalances = BalanceAggregator.aggregateBalances(accountBalances)
    val subAccountBalances = generalLedgerService.getGLPrisonerBalances(prisonNumber)

    if (subAccountBalances.isEmpty()) {
      throw GeneralLedgerAccountNotFoundException("No sub accounts found for prisoner $prisonNumber")
    }

    val discrepancies = mutableListOf<GeneralLedgerDiscrepancyDetails>()

    prisonerSubAccounts.forEach { (subAccountRef, subAccountCode) ->

      val nomisBalance = aggregatedBalances[subAccountCode]?.balance?.toPence()
      val glBalance = subAccountBalances[subAccountRef]?.amount

      val accountNotInNomisData = nomisBalance == null
      val glSubAccountHasNoBalanceInformation = glBalance == 0L
      if (accountNotInNomisData && glSubAccountHasNoBalanceInformation) {
        return@forEach
      }

      val accountNotInGLData = glBalance == null
      val nomisAccountHasNoBalanceInformation = nomisBalance == 0L
      if (accountNotInGLData && nomisAccountHasNoBalanceInformation) {
        return@forEach
      }

      if (nomisBalance != glBalance) {
        val discrepancyDetails = createDiscrepancyReport(
          prisonNumber = prisonNumber,
          subAccountRef = subAccountRef,
          generalLedgerBalance = subAccountBalances[subAccountRef],
          nomisBalances = accountBalances.filter { it.accountCode == prisonerSubAccounts[subAccountRef] },
          glBalanceAmount = glBalance ?: 0L,
          nomisBalanceAmount = nomisBalance ?: 0L,
        )

        discrepancies.add(discrepancyDetails)

        telemetryClient.trackEvent(validationMismatchEventName, discrepancyDetails.toStringMap(), null)
        log.error("Migration balance validation mismatch for prisoner $prisonNumber: ${discrepancyDetails.toStringMap()}")
      }
    }

    return discrepancies
  }
}
