package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.audit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_MESSAGE_PRISON_ID
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.createSyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniqueCaseloadId
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

class AuditHistoryTest(
  @param:Autowired val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
  @param:Autowired val jdbcTemplate: JdbcTemplate,
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
  fun `Get History with no dates returns any payload`() {
    val caseloadId = uniqueCaseloadId()

    val recentPayload = makeNomisSyncPayload(caseloadId, timestamp = Instant.now())

    val oldPayload = makeNomisSyncPayload(caseloadId, timestamp = Instant.EPOCH)

    nomisSyncPayloadRepository.save(recentPayload)
    nomisSyncPayloadRepository.save(oldPayload)

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

  fun randomInstant(): Instant {
    val secondsInDay = 86400L
    val now = Instant.now().epochSecond
    val start = now - (365 * 10 * secondsInDay)
    val randomSeconds = ThreadLocalRandom.current().nextLong(start, now)
    return Instant.ofEpochSecond(randomSeconds)
  }

  @Test
  fun `Get History speed should be performant at any page`() {
    val caseloads = List(30) { uniqueCaseloadId() }
    val totalRecords = 1_000_000
    val batchSize = 1000

    val sql = """
      INSERT INTO nomis_sync_payloads
        (timestamp, legacy_transaction_id, synchronized_transaction_id, request_id,
         caseload_id, request_type_identifier, transaction_timestamp, body)
      VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?::jsonb)
    """.trimIndent()

    for (i in 0 until totalRecords step batchSize) {
      val currentBatchSize = minOf(batchSize, totalRecords - i)

      val batchArgs = List(currentBatchSize) {
        arrayOf(
          Timestamp.from(randomInstant()),
          (1000L + i),
          UUID.randomUUID(),
          UUID.randomUUID(),
          caseloads.random(),
          "SYNC_TYPE",
          Timestamp.from(randomInstant()),
          """{"data": "sample_body_$i"}""",
        )
      }

      jdbcTemplate.batchUpdate(sql, batchArgs)
    }

    val pageSize = 20
    val numOfPages = totalRecords / pageSize
    val pageIdx = listOf(0, numOfPages / 2, numOfPages - 1)

    for (i in pageIdx) {
      val reqTime = Instant.now()
      webTestClient.get()
        .uri {
          it.path("/audit/history")
            .queryParam("page", i)
            .queryParam("size", pageSize)
            .build()
        }
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.content.length()").isEqualTo(pageSize)

      val delta = Duration.between(reqTime, Instant.now()).toMillis()
      assertThat(delta).isLessThan(3000)
    }
  }

  @Test
  fun `Get History with startDate only returns any payload after it`() {
    val caseloadId = uniqueCaseloadId()
    val startDate = LocalDate.now()

    val todayPayload = makeNomisSyncPayload(caseloadId, timestamp = Instant.now())

    val pastPayload = makeNomisSyncPayload(
      caseloadId,
      Instant.now().minus(1, ChronoUnit.DAYS),
    )

    nomisSyncPayloadRepository.save(todayPayload)
    nomisSyncPayloadRepository.save(pastPayload)

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
  fun `Get History with endDate returns any payload before it`() {
    val caseloadId = uniqueCaseloadId()
    val endDate = LocalDate.now()

    val todayPayload = makeNomisSyncPayload(caseloadId)

    val futurePayload = makeNomisSyncPayload(caseloadId, timestamp = Instant.now().plus(1, ChronoUnit.DAYS))

    val veryOldPayload = makeNomisSyncPayload(caseloadId, timestamp = Instant.EPOCH)

    nomisSyncPayloadRepository.save(todayPayload)
    nomisSyncPayloadRepository.save(futurePayload)
    nomisSyncPayloadRepository.save(veryOldPayload)

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
      .jsonPath("$.content[1].synchronizedTransactionId").isEqualTo(veryOldPayload.synchronizedTransactionId.toString())
  }

  @Test
  fun `Get History with startDate and endDate only returns payloads in range`() {
    val caseloadId = uniqueCaseloadId()
    val startDate = LocalDate.now().minusDays(1)
    val endDate = LocalDate.now().plusDays(1)

    val todayPayload = makeNomisSyncPayload(caseloadId, timestamp = Instant.now())

    val notInRangePayload = makeNomisSyncPayload(
      caseloadId,
      timestamp = Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS),
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
      val payload =
        makeNomisSyncPayload(
          caseloadId,
          timestamp = Instant.now().minus(i.toLong(), java.time.temporal.ChronoUnit.DAYS),
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

  @Test
  fun `Get History without any parameter returns any PrisonId, LegacyTransactionId, and date`() {
    nomisSyncPayloadRepository.deleteAll()

    val payload1 = makeNomisSyncPayload(uniqueCaseloadId(), timestamp = Instant.now(), legacyTransactionId = Random.nextLong())
    val payload2 = makeNomisSyncPayload(uniqueCaseloadId(), timestamp = Instant.now().minus(1, ChronoUnit.DAYS), legacyTransactionId = Random.nextLong())
    val payload3 = makeNomisSyncPayload(uniqueCaseloadId(), timestamp = Instant.EPOCH, legacyTransactionId = Random.nextLong())

    val payloads = listOf(payload1, payload2, payload3)

    for (payload in payloads) {
      nomisSyncPayloadRepository.save(payload)
    }

    val res = webTestClient.get()
      .uri("/audit/history")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(payloads.size)

    for (i in 0..payloads.lastIndex) {
      res
        .jsonPath("$.content[$i].synchronizedTransactionId")
        .isEqualTo(payloads[i].synchronizedTransactionId)
        .jsonPath("$.content[$i].legacyTransactionId")
        .isEqualTo(payloads[i].legacyTransactionId)
        .jsonPath("$.content[$i].caseloadId")
        .isEqualTo(payloads[i].caseloadId)
        .jsonPath("$.content[$i].transactionTimestamp").value<String> {
          assertThat(it).startsWith(payloads[i].transactionTimestamp.toString().substring(0, 19))
        }
        .jsonPath("$.content[$i].requestTypeIdentifier")
        .isEqualTo(payloads[i].requestTypeIdentifier)
        .jsonPath("$.content[$i].requestId")
        .isEqualTo(payloads[i].requestId.toString())
        .jsonPath("$.content[$i].timestamp").value<String> {
          assertThat(it).startsWith(payloads[i].timestamp.toString().substring(0, 19))
        }
    }
  }

  @Test
  fun `Get History should filter by LegacyTransactionId when parameter is set`() {
    val lookUpLegTransId = 111L
    val filteredOutLegTransId = 999L
    val lookUpPayload = makeNomisSyncPayload(uniqueCaseloadId(), legacyTransactionId = lookUpLegTransId)
    val filteredOutPayload = makeNomisSyncPayload(uniqueCaseloadId(), legacyTransactionId = filteredOutLegTransId)

    val payloads = listOf(lookUpPayload, filteredOutPayload)

    for (payload in payloads) {
      nomisSyncPayloadRepository.save(payload)
    }

    webTestClient.get()
      .uri("/audit/history?legacyTransactionId={legacyTransactionId}", "$lookUpLegTransId")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].synchronizedTransactionId")
      .isEqualTo(lookUpPayload.synchronizedTransactionId)
      .jsonPath("$.content[0].legacyTransactionId")
      .isEqualTo(lookUpPayload.legacyTransactionId)
      .jsonPath("$.content[0].caseloadId")
      .isEqualTo(lookUpPayload.caseloadId)
      .jsonPath("$.content[0].transactionTimestamp").value<String> {
        assertThat(it).startsWith(lookUpPayload.transactionTimestamp.toString().substring(0, 19))
      }
      .jsonPath("$.content[0].requestTypeIdentifier")
      .isEqualTo(lookUpPayload.requestTypeIdentifier)
      .jsonPath("$.content[0].requestId")
      .isEqualTo(lookUpPayload.requestId.toString())
      .jsonPath("$.content[0].timestamp").value<String> {
        assertThat(it).startsWith(lookUpPayload.timestamp.toString().substring(0, 19))
      }
  }
}
