package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse

@ExtendWith(MockitoExtension::class)
class InMemoryAccountCacheTest {

  private lateinit var cache: InMemoryAccountCache

  private val offenderDisplayId = "A1234AA"

  @BeforeEach
  fun setUp() {
    cache = InMemoryAccountCache()
  }

  @Test
  fun `should return stored value when key already exists in cache`() {
    val account: AccountResponse = mock()

    cache.put(offenderDisplayId, account)

    val accountProvider: () -> AccountResponse = mock()

    val result = cache.getOrPut(offenderDisplayId, accountProvider)

    assertSame(account, result)
    verify(accountProvider, never()).invoke()
    verifyNoMoreInteractions(accountProvider)
  }

  @Test
  fun `should invoke supplier and cache result when key does not exist`() {
    val account: AccountResponse = mock()

    val accountProvider: () -> AccountResponse = mock()
    whenever(accountProvider.invoke()).thenReturn(account)

    val result = cache.getOrPut(offenderDisplayId, accountProvider)

    assertSame(account, result)
    verify(accountProvider, times(1)).invoke()
    verifyNoMoreInteractions(accountProvider)

    // Ensure it was cached
    val secondCall = cache.getOrPut(offenderDisplayId) { mock() }
    assertSame(account, secondCall)
  }

  @Test
  fun `should invoke supplier only once when getOrPut is called multiple times for same key`() {
    val account: AccountResponse = mock()

    val accountProvider: () -> AccountResponse = mock()
    whenever(accountProvider.invoke()).thenReturn(account)

    val first = cache.getOrPut(offenderDisplayId, accountProvider)
    val second = cache.getOrPut(offenderDisplayId, accountProvider)

    assertSame(account, first)
    assertSame(account, second)

    verify(accountProvider, times(1)).invoke()
    verifyNoMoreInteractions(accountProvider)
  }

  @Test
  fun `should override existing value when put is called with same key`() {
    val firstAccount: AccountResponse = mock()
    val secondAccount: AccountResponse = mock()

    cache.put(offenderDisplayId, firstAccount)
    cache.put(offenderDisplayId, secondAccount)

    val accountProvider: () -> AccountResponse = mock()

    val result = cache.getOrPut(offenderDisplayId, accountProvider)

    assertSame(secondAccount, result)
    verify(accountProvider, never()).invoke()
    verifyNoMoreInteractions(accountProvider)
  }
}
