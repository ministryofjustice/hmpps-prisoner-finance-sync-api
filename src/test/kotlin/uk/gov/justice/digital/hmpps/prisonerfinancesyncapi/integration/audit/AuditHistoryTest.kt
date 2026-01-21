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
import java.util.UUID

class AuditHistoryTest(
  @param:Autowired val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
) : IntegrationTestBase() {
  @Test
  fun `Get History should return an empty list when there aren't any payloads`() {
    val caseloadId = uniqueCaseloadId()
    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("content").isEqualTo(emptyList<Any>())
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
      .jsonPath("$.userMessage")
      .value<String> {
        assertThat(it).contains(VALIDATION_MESSAGE_PRISON_ID)
      }
  }

  @Test
  fun `401 unauthorised`() {
    val caseloadId = uniqueCaseloadId()
    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
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

    val offenderTransaction: SyncTransactionReceipt = webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(request)
      .exchange()
      .expectBody(SyncTransactionReceipt::class.java)
      .returnResult()
      .responseBody!!

    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content").isArray
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].synchronizedTransactionId")
      .isEqualTo(offenderTransaction.synchronizedTransactionId.toString())
      .jsonPath("$.content[0].legacyTransactionId")
      .isEqualTo(request.transactionId.toString())
      .jsonPath("$.content[0].caseloadId")
      .isEqualTo(request.caseloadId)
      .jsonPath("$.content[0].transactionTimestamp").value<String> {
        assertThat(it).startsWith(request.transactionTimestamp.toString().substring(0, 19))
      }
      .jsonPath("$.content[0].requestTypeIdentifier")
      .isEqualTo("SyncOffenderTransactionRequest")
      .jsonPath("$.content[0].requestId").exists()
      .jsonPath("$.content[0].timestamp").exists()
      .jsonPath("$.totalElements").isEqualTo(1)
      .jsonPath("$.totalPages").isEqualTo(1)
      .jsonPath("$.number").isEqualTo(0)
      .jsonPath("$.size").exists()
      .jsonPath("$.first").isEqualTo(true)
      .jsonPath("$.last").isEqualTo(true)
  }

  @Test
  fun `Get History should return a list with multiple payloads`() {
    val caseloadId = uniqueCaseloadId()

    val request1 = createSyncOffenderTransactionRequest(caseloadId)
    val request2 = createSyncOffenderTransactionRequest(caseloadId)
    val requests = mutableListOf(request1, request2)

    val offenderTransactions = mutableListOf<SyncTransactionReceipt>()
    for (request in requests) {
      val offenderTransaction: SyncTransactionReceipt = webTestClient
        .post()
        .uri("/sync/offender-transactions")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(request)
        .exchange()
        .expectBody(SyncTransactionReceipt::class.java)
        .returnResult()
        .responseBody!!
      offenderTransactions.add(offenderTransaction)
    }

    // reversed to match page sorting
    offenderTransactions.reverse()
    requests.reverse()

    val res = webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content").isArray
      .jsonPath("$.content.length()").isEqualTo(requests.size)
      .jsonPath("$.totalElements").isEqualTo(requests.size)
      .jsonPath("$.totalPages").isEqualTo(1)
      .jsonPath("$.number").isEqualTo(0)
      .jsonPath("$.size").exists()
      .jsonPath("$.first").isEqualTo(true)
      .jsonPath("$.last").isEqualTo(true)

    for (i in 0..requests.lastIndex) {
      res
        .jsonPath("$.content[$i].synchronizedTransactionId")
        .isEqualTo(offenderTransactions[i].synchronizedTransactionId.toString())
        .jsonPath("$.content[$i].legacyTransactionId")
        .isEqualTo(requests[i].transactionId.toString())
        .jsonPath("$.content[$i].caseloadId")
        .isEqualTo(requests[i].caseloadId)
        .jsonPath("$.content[$i].transactionTimestamp").value<String> {
          assertThat(it).startsWith(requests[i].transactionTimestamp.toString().substring(0, 19))
        }
        .jsonPath("$.content[$i].requestTypeIdentifier")
        .isEqualTo("SyncOffenderTransactionRequest")
        .jsonPath("$.content[$i].requestId").exists()
        .jsonPath("$.content[$i].timestamp").exists()
    }
  }

  @Test
  fun `Get History with no dates defaults to last 30 days`() {
    val caseloadId = uniqueCaseloadId()

    val recentPayload = NomisSyncPayload(
      timestamp = Instant.now(),
      legacyTransactionId = 1003,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      requestTypeIdentifier = "NewSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"new": "data"}""",
      transactionTimestamp = Instant.now(),
    )

    val oldPayload = NomisSyncPayload(
      timestamp = Instant.now().minus(31, java.time.temporal.ChronoUnit.DAYS),
      legacyTransactionId = 1003,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      requestTypeIdentifier = "NewSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"new": "data"}""",
      transactionTimestamp = Instant.now(),
    )

    nomisSyncPayloadRepository.save(recentPayload)
    nomisSyncPayloadRepository.save(oldPayload)

    webTestClient.get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].synchronizedTransactionId").isEqualTo(recentPayload.synchronizedTransactionId.toString())
  }

  @Test
  fun `Get History with startDate only defaults endDate to today`() {
    val caseloadId = uniqueCaseloadId()
    val startDate = LocalDate.now()

    val todayPayload = NomisSyncPayload(
      timestamp = Instant.now(),
      legacyTransactionId = 1003,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      requestTypeIdentifier = "NewSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"new": "data"}""",
      transactionTimestamp = Instant.now(),
    )

    val pastPayload = NomisSyncPayload(
      timestamp = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS),
      legacyTransactionId = 1003,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      requestTypeIdentifier = "NewSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"new": "data"}""",
      transactionTimestamp = Instant.now(),
    )

    val futurePayload = NomisSyncPayload(
      timestamp = Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS),
      legacyTransactionId = 1003,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      requestTypeIdentifier = "NewSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"new": "data"}""",
      transactionTimestamp = Instant.now(),
    )

    nomisSyncPayloadRepository.save(todayPayload)
    nomisSyncPayloadRepository.save(pastPayload)
    nomisSyncPayloadRepository.save(futurePayload)

    webTestClient.get()
      .uri {
        it.path("/audit/history")
          .queryParam("prisonId", caseloadId)
          .queryParam("startDate", startDate)
          .build()
      }
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].synchronizedTransactionId").isEqualTo(todayPayload.synchronizedTransactionId.toString())
  }

  @Test
  fun `Get History with endDate only defaults startDate to 30 days before endDate`() {
    val caseloadId = uniqueCaseloadId()
    val endDate = LocalDate.now()

    val todayPayload = NomisSyncPayload(
      timestamp = Instant.now(),
      legacyTransactionId = 1003,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      requestTypeIdentifier = "NewSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"new": "data"}""",
      transactionTimestamp = Instant.now(),
    )

    val pastPayloadWithin30days = NomisSyncPayload(
      timestamp = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS),
      legacyTransactionId = 1003,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      requestTypeIdentifier = "NewSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"new": "data"}""",
      transactionTimestamp = Instant.now(),
    )

    val pastPayloadOlderThan30Days = NomisSyncPayload(
      timestamp = Instant.now().minus(31, java.time.temporal.ChronoUnit.DAYS),
      legacyTransactionId = 1003,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      requestTypeIdentifier = "NewSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"new": "data"}""",
      transactionTimestamp = Instant.now(),
    )

    nomisSyncPayloadRepository.save(todayPayload)
    nomisSyncPayloadRepository.save(pastPayloadWithin30days)
    nomisSyncPayloadRepository.save(pastPayloadOlderThan30Days)

    webTestClient.get()
      .uri {
        it.path("/audit/history")
          .queryParam("prisonId", caseloadId)
          .queryParam("endDate", endDate)
          .build()
      }
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content[0].synchronizedTransactionId").isEqualTo(todayPayload.synchronizedTransactionId.toString())
      .jsonPath("$.content[1].synchronizedTransactionId").isEqualTo(pastPayloadWithin30days.synchronizedTransactionId.toString())
  }

  @Test
  fun `Get History with startDate and endDate only returns payloads in range`() {
    val caseloadId = uniqueCaseloadId()
    val startDate = LocalDate.now().minusDays(1)
    val endDate = LocalDate.now().plusDays(1)

    val todayPayload = NomisSyncPayload(
      timestamp = Instant.now(),
      legacyTransactionId = 1003,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      requestTypeIdentifier = "NewSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"new": "data"}""",
      transactionTimestamp = Instant.now(),
    )

    val notInRangePayload = NomisSyncPayload(
      timestamp = Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS),
      legacyTransactionId = 1003,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      requestTypeIdentifier = "NewSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"new": "data"}""",
      transactionTimestamp = Instant.now(),
    )

    nomisSyncPayloadRepository.save(todayPayload)
    nomisSyncPayloadRepository.save(notInRangePayload)

    webTestClient.get()
      .uri {
        it.path("/audit/history")
          .queryParam("prisonId", caseloadId)
          .queryParam("startDate", startDate)
          .queryParam("endDate", endDate)
          .build()
      }
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
    val startDate = LocalDate.now().minusDays(30)
    val endDate = LocalDate.now()

    val payloads = mutableListOf<NomisSyncPayload>()

    for (i in 10 downTo 1) {
      val payload = NomisSyncPayload(
        timestamp = Instant.now().minus(i.toLong(), java.time.temporal.ChronoUnit.DAYS),
        legacyTransactionId = 1003,
        requestId = UUID.randomUUID(),
        caseloadId = caseloadId,
        requestTypeIdentifier = "NewSyncType",
        synchronizedTransactionId = UUID.randomUUID(),
        body = """{"new": "data"}""",
        transactionTimestamp = Instant.now(),
      )

      payloads.add(payload)
      nomisSyncPayloadRepository.save(payload)
    }

    val res = webTestClient.get()
      .uri {
        it.path("/audit/history")
          .queryParam("prisonId", caseloadId)
          .queryParam("startDate", startDate)
          .queryParam("endDate", endDate)
          .build()
      }
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(10)

    for (i in 9 downTo 0) {
      res.jsonPath("$.content[${9 - i}].synchronizedTransactionId").isEqualTo(payloads[i].synchronizedTransactionId.toString())
    }
  }
}
