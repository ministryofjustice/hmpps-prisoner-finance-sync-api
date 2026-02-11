package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest

@Service
class BalanceMigrationAggregationService {
  fun aggregate(prisonerBalancesSyncRequestMock: PrisonerBalancesSyncRequest, i: Int): Int = 0
}
