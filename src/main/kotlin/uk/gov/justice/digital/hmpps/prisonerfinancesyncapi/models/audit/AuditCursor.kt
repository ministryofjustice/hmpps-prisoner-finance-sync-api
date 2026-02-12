package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit

import java.time.Instant
import java.util.Base64

data class AuditCursor(val timestamp: Instant, val id: Long) {

  override fun toString(): String {
    val raw = "$timestamp,$id"
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
  }

  companion object {
    fun parse(cursor: String?): AuditCursor? {
      if (cursor.isNullOrBlank()) return null
      return try {
        val decodedBytes = Base64.getUrlDecoder().decode(cursor)
        val decodedString = String(decodedBytes)
        val parts = decodedString.split(",")

        if (parts.size < 2) return null

        AuditCursor(
          timestamp = Instant.parse(parts[0]),
          id = parts[1].toLong(),
        )
      } catch (_: Exception) {
        null
      }
    }
  }
}
