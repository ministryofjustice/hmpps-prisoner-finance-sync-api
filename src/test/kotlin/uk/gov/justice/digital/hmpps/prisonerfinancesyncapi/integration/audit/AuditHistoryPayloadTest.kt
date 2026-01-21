package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.audit

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniqueCaseloadId
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import java.time.Instant
import java.util.UUID

class AuditHistoryPayloadTest(
  @param:Autowired val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
) : IntegrationTestBase() {

  @Test
  fun `Get history payload should return payload when payload exists`() {
    val payload = NomisSyncPayload(
      timestamp = Instant.now(),
      legacyTransactionId = 1003,
      requestId = UUID.randomUUID(),
      caseloadId = uniqueCaseloadId(),
      requestTypeIdentifier = "NewSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"test": "data"}""",
      transactionTimestamp = Instant.now(),
    )

    nomisSyncPayloadRepository.save(payload)

    webTestClient
      .get()
      .uri("/audit/history/payload/{transactionId}", payload.legacyTransactionId)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody(String::class.java)
      .isEqualTo(payload.body)
  }

  @Test
  fun `Get history payload should return 400 BAD request when transactionId is not Long`() {
    webTestClient
      .get()
      .uri("/audit/history/payload/{transactionId}", "breakingwithastring")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `Get history payload should return 404 not found when payload does not exist`() {
    webTestClient
      .get()
      .uri("/audit/history/payload/{transactionId}", 999L)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isNotFound
  }

  @Test
  fun `401 unauthorised`() {
    webTestClient
      .get()
      .uri("/audit/history/payload/{transactionId}", 123L)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - does not have the right role`() {
    webTestClient
      .get()
      .uri("/audit/history/payload/{transactionId}", 123L)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
      .exchange()
      .expectStatus().isForbidden
  }
}
