package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.exceptions.GeneralLedgerAccountNotFoundException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.MigrationBalanceValidationMismatchEvent
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence

@Service
class MigrationValidationService(
  private val generalLedgerService: GeneralLedgerService,
  private val telemetryClient: TelemetryClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(MigrationValidationService::class.java)
  }

  val prisonerSubAccounts = mapOf("CASH" to 2101, "SPENDS" to 2102, "SAVINGS" to 2103)

  fun validatePrisonerBalances(prisonNumber: String, accountBalances: List<PrisonerAccountPointInTimeBalance>): Boolean {
    val aggregatedBalances = BalanceAggregator.aggregateBalances(accountBalances)
    val subAccountBalances = generalLedgerService.getGLPrisonerBalances(prisonNumber)

    if (subAccountBalances.isEmpty()) {
      throw GeneralLedgerAccountNotFoundException("No sub accounts found for prisoner $prisonNumber")
    }

    var validated = true

    subAccountBalances.forEach { (subAccountRef, glBalance) ->

      val subAccountCode = prisonerSubAccounts[subAccountRef]
      val nomisBalance = aggregatedBalances[subAccountCode]?.balance?.toPence()

      val accountNotInNomisData = nomisBalance == null
      val glSubAccountHasNoBalanceInformation = glBalance.amount == 0L

      if (accountNotInNomisData && glSubAccountHasNoBalanceInformation) {
        return@forEach
      }

      if (nomisBalance != glBalance.amount) {
        validated = false
      }
    }

    if (!validated) {
      val mismatchEvent = MigrationBalanceValidationMismatchEvent(
        prisonNumber = prisonNumber,
        nomisBalances = accountBalances,
        aggregatedNomisBalances = aggregatedBalances,
        generalLedgerBalances = subAccountBalances,
      )

      telemetryClient.trackEvent(mismatchEvent.eventName, mismatchEvent.toStringMap(), null)

      log.error("Migration balance validation mismatch for prisoner $prisonNumber: ${mismatchEvent.toStringMap()}")
    }

    return validated
  }
}
