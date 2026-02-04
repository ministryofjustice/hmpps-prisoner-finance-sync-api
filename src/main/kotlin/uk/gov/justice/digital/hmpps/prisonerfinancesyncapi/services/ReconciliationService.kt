package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetailsList

interface ReconciliationService {
  fun reconcilePrisoner(prisonNumber: String): PrisonerEstablishmentBalanceDetailsList
}
