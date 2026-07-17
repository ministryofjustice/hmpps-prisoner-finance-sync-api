package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GeneralLedgerDiscrepancyDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance

@Service
class MigrationValidationService {

  fun validatePrisonerBalances(prisonNumber: String, accountBalances: List<PrisonerAccountPointInTimeBalance>): List<GeneralLedgerDiscrepancyDetails> = emptyList()
}
