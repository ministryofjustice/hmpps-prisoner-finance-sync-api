package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import java.util.UUID

@Service
class GeneralLedgerAccountResolver(
  private val apiClient: GeneralLedgerApiClient,
  private val mapping: LedgerAccountMappingService,
) {
  class RetryAfterConflictException(message: String) : Exception(message)

  private companion object {
    private val log = LoggerFactory.getLogger(GeneralLedgerAccountResolver::class.java)
  }

  fun resolveSubAccount(
    prisonId: String,
    offenderId: String,
    accountCode: Int,
    transactionType: String,
    parentCache: InMemoryAccountCache,
  ): UUID {
    val isPrisoner = mapping.isValidPrisonerAccountCode(accountCode)

    val parentRef = if (isPrisoner) offenderId else prisonId

    val subRef = if (isPrisoner) {
      mapping.mapPrisonerSubAccount(accountCode)
    } else {
      mapping.mapPrisonSubAccount(accountCode, transactionType)
    }

    val parent = parentCache.getOrPut(parentRef) {
      findOrCreateParent(parentRef)
    }

    return getOrCreateSubAccount(parentRef, parent, subRef, parentCache)
  }

  private fun findOrCreateParent(reference: String): AccountResponse {
    val response = apiClient.findAccountByReference(reference)
    if (response != null) {
      return response
    } else {
      log.info("General Ledger account not found for '$reference'. Creating new account.")
      try {
        return apiClient.createAccount(reference)
      } catch (e: WebClientResponseException) {
        if (e.statusCode == HttpStatus.CONFLICT) {
          return apiClient.findAccountByReference(reference)
            ?: throw RetryAfterConflictException("Account not found after server responded with 409 for reference: $reference")
        } else {
          throw e
        }
      }
    }
  }

  private fun getOrCreateSubAccount(
    parentRef: String,
    parent: AccountResponse,
    subRef: String,
    parentCache: InMemoryAccountCache,
  ): UUID {
    parent.subAccounts
      .firstOrNull { it.reference == subRef }
      ?.let { return it.id }

    val created = createSubAccount(parent.id, subRef, parentRef)

    // We need to create a new accountResponse to update the sub accounts
    val updatedParent = parent.copy(
      subAccounts = parent.subAccounts + created,
    )

    parentCache.put(parentRef, updatedParent)

    return created.id
  }

  private fun createSubAccount(parentAccountId: UUID, reference: String, parentReference: String): SubAccountResponse {
    log.info("General Ledger sub-account not found for '$reference'. Creating new sub-account.")
    try {
      return apiClient.createSubAccount(parentAccountId, reference)
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.CONFLICT) {
        return apiClient.findSubAccount(parentReference, reference)
          ?: throw RetryAfterConflictException("Sub account not found after server responded with 409 for reference: $reference")
      } else {
        throw e
      }
    }
  }
}
