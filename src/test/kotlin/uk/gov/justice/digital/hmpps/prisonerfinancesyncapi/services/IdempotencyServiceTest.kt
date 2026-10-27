package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class IdempotencyServiceTest {

  private val service = spy(IdempotencyService())

  @Nested
  inner class GenerateIdempotencyKey {
    @Test
    fun `should generate deterministic UUID for same inputs`() {
      val seed = "12345L-1"

      val first = service.generateIdempotencyKey(seed)
      val second = service.generateIdempotencyKey(seed)

      assertEquals(first, second)
    }

    @Test
    fun `should generate different UUIDs for different seeds`() {
      val first = service.generateIdempotencyKey("SEED-1")
      val second = service.generateIdempotencyKey("SEED-2")

      assertNotEquals(first, second)
    }
  }

  @Test
  fun `should generate different UUIDs for different entry sequences`() {
    val first = service.genGLTransactionIdempotencyKey(12345L, 1)
    val second = service.genGLTransactionIdempotencyKey(12345L, 2)

    assertNotEquals(first, second)
  }

  @Test
  fun `should generate valid RFC 4122 UUIDs`() {
    val uuid = service.genGLTransactionIdempotencyKey(12345L, 1)

    // RFC 4122 requires the Variant to be 2 (IETF Leach-Salz)
    // The PR implementation fails this (it will likely be random or 0)
    assertEquals(2, uuid.variant(), "UUID Variant must be 2 (IETF RFC 4122)")

    // RFC 4122 requires the Version to be 1-5
    // The PR implementation fails this (it will be a random number based on the hash)
    val validVersions = setOf(1, 2, 3, 4, 5)
    if (uuid.version() !in validVersions) {
      throw AssertionError("UUID Version ${uuid.version()} is invalid. Must be one of $validVersions")
    }
  }

  @Nested
  inner class GLTransaction {
    val seedCaptor = argumentCaptor<String>()

    @Test
    fun `should pass same seed for same arguments`() {
      service.genGLTransactionIdempotencyKey(12345L, 1)
      service.genGLTransactionIdempotencyKey(12345L, 1)
      verify(service, times(2)).generateIdempotencyKey(seedCaptor.capture())
      val seeds = seedCaptor.allValues
      assert(seeds.distinct().size == 1)
    }

    @Test
    fun `should pass different seeds to the UUID generator`() {
      service.genGLTransactionIdempotencyKey(12345L, 1)
      service.genGLTransactionIdempotencyKey(56789L, 1)
      service.genGLTransactionIdempotencyKey(12345L, 2)

      verify(service, times(3)).generateIdempotencyKey(seedCaptor.capture())

      val seeds = seedCaptor.allValues

      assert(seeds.distinct().size == seeds.size)
    }
  }

  @Nested
  inner class Subscriptions {
    val seedCaptor = argumentCaptor<String>()

    @Test
    fun `should pass same seed for same arguments`() {
      service.genSubscriptionIdempotencyKey(12345L)
      service.genSubscriptionIdempotencyKey(12345L)
      verify(service, times(2)).generateIdempotencyKey(seedCaptor.capture())
      val seeds = seedCaptor.allValues
      assert(seeds.distinct().size == 1)
    }

    @Test
    fun `should pass different seeds to the UUID generator`() {
      service.genSubscriptionIdempotencyKey(12345L)
      service.genSubscriptionIdempotencyKey(56789L)

      verify(service, times(2)).generateIdempotencyKey(seedCaptor.capture())

      val seeds = seedCaptor.allValues

      assert(seeds.distinct().size == seeds.size)
    }
  }
}
