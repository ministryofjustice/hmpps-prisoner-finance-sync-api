package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AuditCursorTest {

  @Test
  fun `should encode and decode a valid cursor`() {
    val timestamp = Instant.parse("2026-02-12T14:00:00Z")
    val original = AuditCursor(timestamp, 12345L)

    val encoded = original.toString()
    val decoded = AuditCursor.parse(encoded)

    assertThat(decoded).isEqualTo(original)
    assertThat(decoded?.timestamp).isEqualTo(timestamp)
    assertThat(decoded?.id).isEqualTo(12345L)
  }

  @Test
  fun `should handle null or blank input`() {
    assertThat(AuditCursor.parse(null)).isNull()
    assertThat(AuditCursor.parse("   ")).isNull()
  }

  @Test
  fun `should handle malformed base64 strings`() {
    assertThat(AuditCursor.parse("!!!not_base64!!!")).isNull()
  }

  @Test
  fun `should handle valid base64 but invalid content`() {
    val invalidContent = "anVzdF9hX3N0cmluZw"
    assertThat(AuditCursor.parse(invalidContent)).isNull()
  }

  @Test
  fun `should handle missing timestamp or ID in decoded string`() {
    val missingId = "MjAyNi0wMi0xMlQxNDowMDowMFo"
    assertThat(AuditCursor.parse(missingId)).isNull()
  }
}
