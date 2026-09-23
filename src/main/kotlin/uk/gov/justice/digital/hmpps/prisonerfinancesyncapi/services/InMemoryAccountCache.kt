package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse

@Component
class InMemoryAccountCache : AccountCache {
  private val store = mutableMapOf<String, AccountResponse>()

  override fun put(parentRef: String, account: AccountResponse) {
    store[parentRef] = account
  }

  override fun getOrPut(
    parentRef: String,
    supplier: () -> AccountResponse,
  ): AccountResponse = store.getOrPut(parentRef) { supplier() }

  override fun clear() {
    this.store.clear()
  }
}
