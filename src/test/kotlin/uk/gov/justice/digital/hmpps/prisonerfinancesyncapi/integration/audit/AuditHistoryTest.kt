package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.audit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_MESSAGE_PRISON_ID
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.createSyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniqueCaseloadId
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

class AuditHistoryTest(
  @param:Autowired val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
) : IntegrationTestBase() {

  fun makeNomisSyncPayload(
    caseloadId: String,
    timestamp: Instant = Instant.now(),
    legacyTransactionId: Long = 1003,
    requestId: UUID = UUID.randomUUID(),
    requestTypeIdentifier: String = "NewSyncType",
    synchronizedTransactionId: UUID = UUID.randomUUID(),
    body: String = """{"new": "data"}""",
    transactionTimestamp: Instant = Instant.now(),
  ) = NomisSyncPayload(
    timestamp = timestamp,
    legacyTransactionId = legacyTransactionId,
    requestId = requestId,
    caseloadId = caseloadId,
    requestTypeIdentifier = requestTypeIdentifier,
    synchronizedTransactionId = synchronizedTransactionId,
    body = body,
    transactionType = "TEST",
    transactionTimestamp = transactionTimestamp,
  )

  @Test
  fun `Get History should return an empty list when there aren't any payloads`() {
    val caseloadId = uniqueCaseloadId()
    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("$.content").isEqualTo(emptyList<Any>())
  }

  @Test
  fun `Get History should return Bad Request when prison number is invalid`() {
    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", "asdasdaassdadsa123123")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.status").isEqualTo(400)
      .jsonPath("$.userMessage").value<String> { assertThat(it).contains(VALIDATION_MESSAGE_PRISON_ID) }
  }

  @Test
  fun `401 unauthorised`() {
    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", uniqueCaseloadId())
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - does not have the right role`() {
    val caseloadId = uniqueCaseloadId()
    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `Get History should return a list with a payload`() {
    val caseloadId = uniqueCaseloadId()
    val request = createSyncOffenderTransactionRequest(caseloadId)

    val receipt: SyncTransactionReceipt = webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(request)
      .exchange()
      .expectBody(SyncTransactionReceipt::class.java)
      .returnResult().responseBody!!

    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content").isArray
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].synchronizedTransactionId").isEqualTo(receipt.synchronizedTransactionId.toString())
      .jsonPath("$.content[0].legacyTransactionId").isEqualTo(request.transactionId.toString())
      .jsonPath("$.content[0].caseloadId").isEqualTo(request.caseloadId)
      .jsonPath("$.content[0].transactionTimestamp").value<String> {
        assertThat(it).startsWith(request.transactionTimestamp.toString().substring(0, 19))
      }
      .jsonPath("$.content[0].requestTypeIdentifier").isEqualTo("SyncOffenderTransactionRequest")
      .jsonPath("$.content[0].requestId").exists()
      .jsonPath("$.content[0].timestamp").exists()
      .jsonPath("$.totalElements").isEqualTo(1)
      .jsonPath("$.nextCursor").isEmpty
  }

  @Test
  fun `Get History should handle cursor pagination correctly`() {
    val caseloadId = uniqueCaseloadId()
    val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = now, legacyTransactionId = 101))
    nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = now.minusSeconds(1), legacyTransactionId = 102))
    nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = now.minusSeconds(2), legacyTransactionId = 103))

    val firstPageResponse = webTestClient.get()
      .uri("/audit/history?prisonId={prisonId}&size=2", caseloadId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content[0].legacyTransactionId").isEqualTo(101)
      .jsonPath("$.content[1].legacyTransactionId").isEqualTo(102)
      .jsonPath("$.nextCursor").exists()
      .returnResult().responseBodyContent?.let { String(it) }

    val nextCursor = firstPageResponse?.let { com.jayway.jsonpath.JsonPath.read<String>(it, "$.nextCursor") }

    webTestClient.get()
      .uri("/audit/history?prisonId={prisonId}&size=2&cursor={cursor}", caseloadId, nextCursor)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].legacyTransactionId").isEqualTo(103)
      .jsonPath("$.nextCursor").isEmpty
  }

  @Test
  fun `Get History with no dates returns any payload`() {
    val caseloadId = uniqueCaseloadId()
    val recentPayload = nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = Instant.now()))
    val oldPayload = nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = Instant.EPOCH))

    webTestClient.get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content[0].synchronizedTransactionId").isEqualTo(recentPayload.synchronizedTransactionId.toString())
      .jsonPath("$.content[1].synchronizedTransactionId").isEqualTo(oldPayload.synchronizedTransactionId.toString())
  }

  @Test
  fun `Get History with startDate only returns any payload after it`() {
    val caseloadId = uniqueCaseloadId()
    val startDate = LocalDate.now()
    val todayPayload = nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = Instant.now()))
    nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = Instant.now().minus(1, ChronoUnit.DAYS)))

    webTestClient.get()
      .uri { it.path("/audit/history").queryParam("prisonId", caseloadId).queryParam("startDate", startDate).build() }
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].synchronizedTransactionId").isEqualTo(todayPayload.synchronizedTransactionId.toString())
  }

  @Test
  fun `Get History with endDate returns any payload before it`() {
    val caseloadId = uniqueCaseloadId()
    val endDate = LocalDate.now()
    val todayPayload = nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId))
    nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = Instant.now().plus(1, ChronoUnit.DAYS)))
    val veryOldPayload = nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = Instant.EPOCH))

    webTestClient.get()
      .uri { it.path("/audit/history").queryParam("prisonId", caseloadId).queryParam("endDate", endDate).build() }
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content[0].synchronizedTransactionId").isEqualTo(todayPayload.synchronizedTransactionId.toString())
      .jsonPath("$.content[1].synchronizedTransactionId").isEqualTo(veryOldPayload.synchronizedTransactionId.toString())
  }

  @Test
  fun `Get History with startDate and endDate only returns payloads in range`() {
    val caseloadId = uniqueCaseloadId()
    val startDate = LocalDate.now().minusDays(1)
    val endDate = LocalDate.now().plusDays(1)
    val todayPayload = nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = Instant.now()))
    nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = Instant.now().minus(10, ChronoUnit.DAYS)))

    webTestClient.get()
      .uri { it.path("/audit/history").queryParam("prisonId", caseloadId).queryParam("startDate", startDate).queryParam("endDate", endDate).build() }
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].synchronizedTransactionId").isEqualTo(todayPayload.synchronizedTransactionId.toString())
  }

  @Test
  fun `Get History return payloads in Desc order`() {
    val caseloadId = uniqueCaseloadId()
    val payloads = (1..10).map { i ->
      nomisSyncPayloadRepository.save(makeNomisSyncPayload(caseloadId, timestamp = Instant.now().minus(i.toLong(), ChronoUnit.DAYS)))
    }

    val res = webTestClient.get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(10)

    for (i in 0..9) {
      res.jsonPath("$.content[$i].synchronizedTransactionId").isEqualTo(payloads[i].synchronizedTransactionId.toString())
    }
  }

  @Test
  fun `Get History without any parameter returns any PrisonId, LegacyTransactionId, and date`() {
    nomisSyncPayloadRepository.deleteAll()
    val payloads = (1..3).map {
      nomisSyncPayloadRepository.save(makeNomisSyncPayload(uniqueCaseloadId(), legacyTransactionId = Random.nextLong(1000, 9999)))
    }.sortedByDescending { it.timestamp }

    val res = webTestClient.get()
      .uri("/audit/history")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(3)

    for (i in 0..2) {
      res.jsonPath("$.content[$i].requestId").isEqualTo(payloads[i].requestId.toString())
        .jsonPath("$.content[$i].legacyTransactionId").isEqualTo(payloads[i].legacyTransactionId)
    }
  }

  @Test
  fun `Get History should filter by LegacyTransactionId when parameter is set`() {
    val targetId = 111L
    val payload = nomisSyncPayloadRepository.save(makeNomisSyncPayload(uniqueCaseloadId(), legacyTransactionId = targetId))
    nomisSyncPayloadRepository.save(makeNomisSyncPayload(uniqueCaseloadId(), legacyTransactionId = 999L))

    webTestClient.get()
      .uri("/audit/history?legacyTransactionId={id}", targetId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].legacyTransactionId").isEqualTo(targetId)
      .jsonPath("$.content[0].synchronizedTransactionId").isEqualTo(payload.synchronizedTransactionId.toString())
  }
}
