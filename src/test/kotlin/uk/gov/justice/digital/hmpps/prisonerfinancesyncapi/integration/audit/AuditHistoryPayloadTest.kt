package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.audit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniqueCaseloadId
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadDetail
import java.time.Instant
import java.time.temporal.ChronoUnit
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
      .uri("/audit/history/{requestId}", payload.requestId)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody(NomisSyncPayloadDetail::class.java)
      .consumeWith { response ->
        val body = response.responseBody!!

        assertThat(body.requestId).isEqualTo(payload.requestId)
        assertThat(body.legacyTransactionId).isEqualTo(payload.legacyTransactionId)
        assertThat(body.caseloadId).isEqualTo(payload.caseloadId)
        assertThat(body.requestTypeIdentifier).isEqualTo(payload.requestTypeIdentifier)
        assertThat(body.body).isEqualTo(payload.body)

        assertThat(body.timestamp)
          .isCloseTo(payload.timestamp, within(1, ChronoUnit.MILLIS))

        assertThat(body.transactionTimestamp)
          .isCloseTo(payload.transactionTimestamp, within(1, ChronoUnit.MILLIS))
      }
  }

  @Test
  fun `Get history payload should return 400 BAD request when requestId is not the correct type`() {
    webTestClient
      .get()
      .uri("/audit/history/{requestId}", "breakingwithastring")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `Get history payload should return 404 not found when payload does not exist`() {
    webTestClient
      .get()
      .uri("/audit/history/{requestId}", UUID.randomUUID())
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isNotFound
  }

  @Test
  fun `401 unauthorised`() {
    webTestClient
      .get()
      .uri("/audit/history/{requestId}", UUID.randomUUID())
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - does not have the right role`() {
    webTestClient
      .get()
      .uri("/audit/history/{requestId}", UUID.randomUUID())
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
      .exchange()
      .expectStatus().isForbidden
  }
}
