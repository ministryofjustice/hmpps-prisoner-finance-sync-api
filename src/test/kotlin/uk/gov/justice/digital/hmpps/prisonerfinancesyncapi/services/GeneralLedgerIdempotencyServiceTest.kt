package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class GeneralLedgerIdempotencyServiceTest {

  private val service = GeneralLedgerIdempotencyService()

  @Test
  fun `should generate deterministic UUID for same inputs`() {
    val first = service.genTransactionIdempotencyKey(12345L, 1)
    val second = service.genTransactionIdempotencyKey(12345L, 1)

    assertEquals(first, second)
  }

  @Test
  fun `should generate different UUIDs for different transaction ids`() {
    val first = service.genTransactionIdempotencyKey(12345L, 1)
    val second = service.genTransactionIdempotencyKey(54321L, 1)

    assertNotEquals(first, second)
  }

  @Test
  fun `should generate different UUIDs for different entry sequences`() {
    val first = service.genTransactionIdempotencyKey(12345L, 1)
    val second = service.genTransactionIdempotencyKey(12345L, 2)

    assertNotEquals(first, second)
  }
}
