package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.toDetail
import java.time.Instant
import java.util.UUID

class NomisSyncDetailTest {
  @Test
  fun `toDetail maps all fields correctly`() {
    val payload = NomisSyncPayload(
      timestamp = Instant.now(),
      legacyTransactionId = 123L,
      synchronizedTransactionId = UUID.randomUUID(),
      requestId = UUID.randomUUID(),
      caseloadId = "MDI",
      requestTypeIdentifier = "TEST",
      transactionTimestamp = Instant.now(),
      transactionType = "TEST",
      body = """{"key":"value"}""",
    )

    val result = payload.toDetail()

    assertEquals(payload.timestamp, result.timestamp)
    assertEquals(payload.legacyTransactionId, result.legacyTransactionId)
    assertEquals(payload.synchronizedTransactionId, result.synchronizedTransactionId)
    assertEquals(payload.requestId, result.requestId)
    assertEquals(payload.caseloadId, result.caseloadId)
    assertEquals(payload.requestTypeIdentifier, result.requestTypeIdentifier)
    assertEquals(payload.transactionTimestamp, result.transactionTimestamp)
    assertEquals(payload.body, result.body)
  }
}
