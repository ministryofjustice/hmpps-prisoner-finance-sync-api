package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse

interface AccountCache {
  fun put(parentRef: String, account: AccountResponse)
  fun getOrPut(
    parentRef: String,
    supplier: () -> AccountResponse,
  ): AccountResponse
}
