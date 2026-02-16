package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse

class InMemoryAccountCache : AccountCache {
  private val store = mutableMapOf<String, AccountResponse>()

  override fun put(parentRef: String, account: AccountResponse) {
    store[parentRef] = account
  }

  override fun getOrPut(
    parentRef: String,
    supplier: () -> AccountResponse,
  ): AccountResponse = store.getOrPut(parentRef) { supplier() }
}
