package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import java.time.Instant
import java.util.UUID
import kotlin.collections.emptyList

@ExtendWith(MockitoExtension::class)
class GeneralLedgerAccountResolverTest {

  @Mock
  private lateinit var apiClient: GeneralLedgerApiClient

  @Spy
  private lateinit var mapping: LedgerAccountMappingService

  @InjectMocks
  private lateinit var accountResolver: GeneralLedgerAccountResolver

  @Test
  fun `should return existing prisoner sub account id when sub account already exists`() {
    val prisonId = "MDI"
    val offenderId = "A1234BC"
    val entryCode = 2101
    val transactionType = "CANT"

    val parentId = UUID.randomUUID()
    val subId = UUID.randomUUID()

    val existingSub = SubAccountResponse(
      id = subId,
      reference = "CASH",
      parentAccountId = parentId,
      createdBy = "test-user",
      createdAt = Instant.now(),
    )

    val parent = AccountResponse(
      id = parentId,
      reference = offenderId,
      createdBy = "test-user",
      createdAt = Instant.now(),
      subAccounts = listOf(existingSub),
    )

    val cache = InMemoryAccountCache().apply {
      put(offenderId, parent)
    }

    val result = accountResolver.resolveSubAccount(prisonId, offenderId, entryCode, transactionType, cache)

    assertEquals(subId, result)
    verify(apiClient, never()).createSubAccount(any(), any())
    verifyNoMoreInteractions(apiClient)
  }

  @Test
  fun `should create sub account when sub account does not exist for prisoner`() {
    val prisonId = "MDI"
    val offenderId = "A1234BC"
    val entryCode = 2101
    val transactionType = "CANT"

    val parentId = UUID.randomUUID()
    val subId = UUID.randomUUID()

    val parent = AccountResponse(
      id = parentId,
      reference = offenderId,
      createdBy = "test-user",
      createdAt = Instant.now(),
      subAccounts = emptyList(),
    )

    val createdSub = SubAccountResponse(
      id = subId,
      reference = "CASH",
      parentAccountId = parentId,
      createdBy = "test-user",
      createdAt = Instant.now(),
    )

    whenever(apiClient.createSubAccount(parentId, "CASH")).thenReturn(createdSub)

    val cache = InMemoryAccountCache().apply {
      put(offenderId, parent)
    }

    val result = accountResolver.resolveSubAccount(prisonId, offenderId, entryCode, transactionType, cache)

    assertEquals(subId, result)
    verify(apiClient).createSubAccount(parentId, "CASH")
  }

  @Test
  fun `should find existing parent account when not present in cache`() {
    val prisonId = "MDI"
    val offenderId = "A1234BC"
    val entryCode = 2101
    val transactionType = "CANT"

    val parentId = UUID.randomUUID()

    val parent = AccountResponse(
      id = parentId,
      reference = offenderId,
      createdBy = "test-user",
      createdAt = Instant.now(),
      subAccounts = emptyList(),
    )

    whenever(apiClient.findAccountByReference(offenderId)).thenReturn(parent)
    whenever(apiClient.createSubAccount(parentId, "CASH"))
      .thenReturn(
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "CASH",
          parentAccountId = parentId,
          createdBy = "test-user",
          createdAt = Instant.now(),
        ),
      )

    val cache = InMemoryAccountCache()

    accountResolver.resolveSubAccount(prisonId, offenderId, entryCode, transactionType, cache)

    verify(apiClient).findAccountByReference(offenderId)
    verify(apiClient, never()).createAccount(any())
  }

  @Test
  fun `should create parent account when not found by reference`() {
    val prisonId = "MDI"
    val offenderId = "A1234BC"
    val entryCode = 2101
    val transactionType = "CANT"

    val parentId = UUID.randomUUID()

    whenever(apiClient.findAccountByReference(offenderId)).thenReturn(null)
    whenever(apiClient.createAccount(offenderId))
      .thenReturn(
        AccountResponse(
          id = parentId,
          reference = offenderId,
          createdBy = "test-user",
          createdAt = Instant.now(),
          subAccounts = emptyList(),
        ),
      )

    whenever(apiClient.createSubAccount(eq(parentId), any()))
      .thenReturn(
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "CASH",
          parentAccountId = parentId,
          createdBy = "test-user",
          createdAt = Instant.now(),
        ),
      )

    val cache = InMemoryAccountCache()

    accountResolver.resolveSubAccount(prisonId, offenderId, entryCode, transactionType, cache)

    verify(apiClient).findAccountByReference(offenderId)
    verify(apiClient).createAccount(offenderId)
  }

  @Test
  fun `should use prison mapping when entry code is not prisoner type`() {
    val prisonId = "MDI"
    val offenderId = "A1234BC"
    val entryCode = 1502
    val transactionType = "ADV"

    val subAccountReference = mapping.mapPrisonSubAccount(entryCode, transactionType)

    val parentId = UUID.randomUUID()

    val parent = AccountResponse(
      id = parentId,
      reference = "REF",
      createdBy = "test-user",
      createdAt = Instant.now(),
      subAccounts = emptyList(),
    )

    whenever(apiClient.findAccountByReference(prisonId)).thenReturn(parent)
    whenever(apiClient.createSubAccount(parentId, subAccountReference))
      .thenReturn(
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = subAccountReference,
          parentAccountId = parentId,
          createdBy = "test-user",
          createdAt = Instant.now(),
        ),
      )

    val cache = InMemoryAccountCache()

    accountResolver.resolveSubAccount(prisonId, offenderId, entryCode, transactionType, cache)
  }

  @Test
  fun `should try to get parent account again when create account throws exception 409`() {
    val prisonId = "MDI"
    val offenderId = "A1234BC"
    val entryCode = 1501
    val transactionType = "CANT"

    val parentId = UUID.randomUUID()

    whenever(apiClient.findAccountByReference(prisonId))
      .thenReturn(null)
      .thenReturn(
        AccountResponse(
          id = parentId,
          reference = offenderId,
          createdBy = "test-user",
          createdAt = Instant.now(),
          subAccounts = emptyList(),
        ),
      )

    whenever(apiClient.createAccount(prisonId))
      .thenThrow(WebClientResponseException(409, "Duplicate account reference: $offenderId", null, null, null))

    whenever(apiClient.createSubAccount(eq(parentId), any()))
      .thenReturn(
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "CASH",
          parentAccountId = parentId,
          createdBy = "test-user",
          createdAt = Instant.now(),
        ),
      )

    val cache = InMemoryAccountCache()

    accountResolver.resolveSubAccount(prisonId, offenderId, entryCode, transactionType, cache)

    verify(apiClient, times(2)).findAccountByReference(prisonId)
    verify(apiClient).createAccount(prisonId)
  }

  @Test
  fun `should try to get sub account again when create account throws exception 409`() {
    val prisonId = "MDI"
    val offenderId = "A1234BC"
    val entryCode = 2101
    val transactionType = "CANT"
    val subAccountReference = "CASH"

    val parentId = UUID.randomUUID()

    whenever(apiClient.findAccountByReference(offenderId))
      .thenReturn(
        AccountResponse(
          id = parentId,
          reference = offenderId,
          createdBy = "test-user",
          createdAt = Instant.now(),
          subAccounts = emptyList(),
        ),
      )

    whenever(apiClient.createSubAccount(eq(parentId), any()))
      .thenThrow(WebClientResponseException(409, "Duplicate account reference: $offenderId", null, null, null))

    whenever(apiClient.findSubAccount(offenderId, subAccountReference))
      .thenReturn(
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = subAccountReference,
          parentAccountId = parentId,
          createdBy = "test-user",
          createdAt = Instant.now(),
        ),
      )

    val cache = InMemoryAccountCache()

    accountResolver.resolveSubAccount(prisonId, offenderId, entryCode, transactionType, cache)

    verify(apiClient).findAccountByReference(offenderId)
    verify(apiClient).findSubAccount(offenderId, subAccountReference)
    verify(apiClient).createSubAccount(parentId, subAccountReference)
  }

  @Test
  fun `should throw RetryAfterConflictException when second get fails after conflict 409 on parent account`() {
    val prisonId = "MDI"
    val offenderId = "A1234BC"
    val entryCode = 1501
    val transactionType = "CANT"

    whenever(apiClient.findAccountByReference(prisonId))
      .thenReturn(null)
      .thenReturn(null)

    whenever(apiClient.createAccount(prisonId))
      .thenThrow(WebClientResponseException(409, "Duplicate account reference: $offenderId", null, null, null))

    val cache = InMemoryAccountCache()

    assertThrows<GeneralLedgerAccountResolver.RetryAfterConflictException> {
      accountResolver.resolveSubAccount(prisonId, offenderId, entryCode, transactionType, cache)
    }

    verify(apiClient, times(2)).findAccountByReference(prisonId)
    verify(apiClient).createAccount(prisonId)
  }

  @Test
  fun `should throw RetryAfterConflictException when get fails after conflict 409 on sub account`() {
    val prisonId = "MDI"
    val offenderId = "A1234BC"
    val entryCode = 2101
    val transactionType = "CANT"
    val subAccountReference = "CASH"

    val parentId = UUID.randomUUID()

    whenever(apiClient.findAccountByReference(offenderId))
      .thenReturn(
        AccountResponse(
          id = parentId,
          reference = offenderId,
          createdBy = "test-user",
          createdAt = Instant.now(),
          subAccounts = emptyList(),
        ),
      )

    whenever(apiClient.createSubAccount(eq(parentId), any()))
      .thenThrow(WebClientResponseException(409, "Duplicate account reference: $offenderId", null, null, null))

    whenever(apiClient.findSubAccount(offenderId, subAccountReference))
      .thenReturn(null)

    val cache = InMemoryAccountCache()

    assertThrows<GeneralLedgerAccountResolver.RetryAfterConflictException> {
      accountResolver.resolveSubAccount(prisonId, offenderId, entryCode, transactionType, cache)
    }

    verify(apiClient).findAccountByReference(offenderId)
    verify(apiClient).findSubAccount(offenderId, subAccountReference)
    verify(apiClient).createSubAccount(parentId, subAccountReference)
  }

  @Test
  fun `should propagate Exception when POST parent account`() {
    val prisonId = "MDI"
    val offenderId = "A1234BC"
    val entryCode = 1501
    val transactionType = "CANT"

    whenever(apiClient.findAccountByReference(prisonId))
      .thenReturn(null)

    whenever(apiClient.createAccount(prisonId))
      .thenThrow(RuntimeException("API Unavailable"))

    val cache = InMemoryAccountCache()

    assertThrows<RuntimeException> {
      accountResolver.resolveSubAccount(prisonId, offenderId, entryCode, transactionType, cache)
    }

    verify(apiClient).findAccountByReference(prisonId)
    verify(apiClient).createAccount(prisonId)
  }

  @Test
  fun `should propagate Exception when POST sub account`() {
    val prisonId = "MDI"
    val offenderId = "A1234BC"
    val entryCode = 2101
    val transactionType = "CANT"
    val subAccountReference = "CASH"

    val parentId = UUID.randomUUID()

    whenever(apiClient.findAccountByReference(offenderId))
      .thenReturn(
        AccountResponse(
          id = parentId,
          reference = offenderId,
          createdBy = "test-user",
          createdAt = Instant.now(),
          subAccounts = emptyList(),
        ),
      )

    whenever(apiClient.createSubAccount(eq(parentId), any()))
      .thenThrow(RuntimeException("API Unavailable"))

    val cache = InMemoryAccountCache()

    assertThrows<RuntimeException> {
      accountResolver.resolveSubAccount(prisonId, offenderId, entryCode, transactionType, cache)
    }

    verify(apiClient).findAccountByReference(offenderId)
    verify(apiClient, never()).findSubAccount(offenderId, subAccountReference)
    verify(apiClient).createSubAccount(parentId, subAccountReference)
  }
}
