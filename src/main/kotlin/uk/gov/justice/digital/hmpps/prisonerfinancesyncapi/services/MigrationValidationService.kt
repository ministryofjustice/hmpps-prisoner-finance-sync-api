package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.exceptions.GeneralLedgerAccountNotFoundException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.DiscrepancyProperties
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GeneralLedgerDiscrepancyDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import kotlin.math.abs

@Service
class MigrationValidationService(
) {



  fun validatePrisonerBalances(prisonNumber: String, accountBalances: List<PrisonerAccountPointInTimeBalance>): List<GeneralLedgerDiscrepancyDetails> {
    return emptyList()
  }
}
