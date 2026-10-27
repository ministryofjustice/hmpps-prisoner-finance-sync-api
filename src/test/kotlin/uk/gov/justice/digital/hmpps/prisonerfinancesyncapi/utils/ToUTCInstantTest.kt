package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime

class ToUTCInstantTest {

  @Test
  fun `should convert GMT (Winter) LocalDateTime to correct UTC Instant`() {
    val localDateTime = LocalDateTime.of(2023, 1, 15, 12, 30)

    val result = localDateTime.toUtcInstant()

    assertEquals(Instant.parse("2023-01-15T12:30:00Z"), result)
  }

  @Test
  fun `should convert BST (Summer) LocalDateTime to correct UTC Instant`() {
    val localDateTime = LocalDateTime.of(2023, 7, 15, 12, 30)

    val result = localDateTime.toUtcInstant()

    assertEquals(Instant.parse("2023-07-15T11:30:00Z"), result)
  }

  @Test
  fun `should handle transition into BST (Spring Forward)`() {
    val localDateTime = LocalDateTime.of(2023, 3, 26, 2, 0)

    val result = localDateTime.toUtcInstant()

    assertEquals(Instant.parse("2023-03-26T01:00:00Z"), result)
  }

  @Test
  fun `should handle transition out of BST (Autumn Back)`() {
    val localDateTime = LocalDateTime.of(2023, 10, 29, 1, 30)

    val result = localDateTime.toUtcInstant()

    assertEquals(Instant.parse("2023-10-29T00:30:00Z"), result)
  }
}
