package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.jpa.repository

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.RepositoryTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class NomisSyncPayloadRepositoryTest(
  @param:Autowired val entityManager: TestEntityManager,
  @param:Autowired val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
) : RepositoryTestBase() {

  private val requestType1 = "SyncOffenderTransaction"
  private lateinit var payload1: NomisSyncPayload
  private lateinit var payload2: NomisSyncPayload
  private lateinit var payload3: NomisSyncPayload
  private lateinit var payload4: NomisSyncPayload
  private lateinit var payload5: NomisSyncPayload

  private val initialPayloadCount = 5

  @BeforeEach
  fun setup() {
    nomisSyncPayloadRepository.deleteAll()
    entityManager.flush()

    val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    val synchronizedTransactionId1 = UUID.randomUUID()
    val synchronizedTransactionId2 = UUID.randomUUID()

    payload1 = createAndPersist(
      timestamp = now.minus(10, ChronoUnit.MINUTES),
      legacyTransactionId = 1001,
      caseloadId = "MDI",
      requestType = requestType1,
      syncTxId = synchronizedTransactionId1,
    )

    payload2 = createAndPersist(
      timestamp = now.minus(5, ChronoUnit.MINUTES),
      legacyTransactionId = 1002,
      caseloadId = "LEI",
      requestType = requestType1,
      syncTxId = synchronizedTransactionId2,
    )

    payload3 = createAndPersist(
      timestamp = now.minus(15, ChronoUnit.MINUTES),
      legacyTransactionId = 1001,
      caseloadId = "MDI",
      requestType = requestType1,
      syncTxId = synchronizedTransactionId1,
    )

    payload4 = createAndPersist(
      timestamp = now.minus(2, ChronoUnit.MINUTES),
      legacyTransactionId = 1004,
      caseloadId = "MDI",
      requestType = "AnotherSyncType",
      syncTxId = UUID.randomUUID(),
    )

    payload5 = createAndPersist(
      timestamp = now.minus(2, ChronoUnit.MINUTES),
      legacyTransactionId = 1004,
      caseloadId = "MDI",
      requestType = "AnotherSyncType",
      transactionType = "UniqueTxn",
      syncTxId = UUID.randomUUID(),
    )
  }

  private fun createAndPersist(
    timestamp: Instant,
    legacyTransactionId: Long,
    caseloadId: String,
    requestType: String,
    syncTxId: UUID,
    transactionType: String = "TEST",
  ): NomisSyncPayload {
    val payload = NomisSyncPayload(
      timestamp = timestamp,
      legacyTransactionId = legacyTransactionId,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      requestTypeIdentifier = requestType,
      synchronizedTransactionId = syncTxId,
      body = "{}",
      transactionType = transactionType,
      transactionTimestamp = timestamp.minus(1, ChronoUnit.DAYS),
    )
    return entityManager.persistAndFlush(payload)
  }

  @Nested
  @DisplayName("findMatchingPayloads")
  inner class FindMatchingPayloads {

    @Test
    fun `should find payloads with stable ordering and tie-breaker cursor`() {
      val collisionTime = Instant.now().truncatedTo(ChronoUnit.SECONDS)
      val pLow = createAndPersist(collisionTime, 5001, "MDI", "TEST", UUID.randomUUID())
      val pHigh = createAndPersist(collisionTime, 5002, "MDI", "TEST", UUID.randomUUID())

      val pageable = PageRequest.of(0, 10)

      val initialResults = nomisSyncPayloadRepository.findMatchingPayloads(
        "MDI",
        null,
        null,
        null,
        null,
        null,
        null,
        pageable,
      )
      assertThat(initialResults.map { it.requestId }).containsSubsequence(pHigh.requestId, pLow.requestId)

      val cursorResults = nomisSyncPayloadRepository.findMatchingPayloads(
        "MDI",
        null,
        null,
        null,
        null,
        pHigh.timestamp,
        pHigh.id!!,
        pageable,
      )
      assertThat(cursorResults.map { it.requestId }).contains(pLow.requestId)
      assertThat(cursorResults.map { it.requestId }).doesNotContain(pHigh.requestId)
    }

    @Test
    fun `should correctly filter by transactionType`() {
      val transactionType = payload5.transactionType
      val pageable = PageRequest.of(0, 10)

      val results = nomisSyncPayloadRepository.findMatchingPayloads(null, null, transactionType, null, null, null, null, pageable)

      assertThat(results).hasSize(1)
      assertThat(results[0].transactionType).isEqualTo(transactionType)
    }

    @Test
    fun `should correctly filter by prisonId and date range`() {
      val start = Instant.now().minus(1, ChronoUnit.HOURS)
      val end = Instant.now().plus(1, ChronoUnit.HOURS)
      val pageable = PageRequest.of(0, 10)

      val results = nomisSyncPayloadRepository.findMatchingPayloads("LEI", null, null, start, end, null, null, pageable)

      assertThat(results).hasSize(1)
      assertThat(results[0].caseloadId).isEqualTo("LEI")
    }

    @Test
    fun `should count results without applying cursor logic`() {
      val count = nomisSyncPayloadRepository.countMatchingPayloads("MDI", null, null, null, null)
      assertThat(count).isEqualTo(4)
    }
  }

  @Nested
  @DisplayName("save")
  inner class Save {
    @Test
    fun `should save a NomisSyncPayload`() {
      val newPayload = NomisSyncPayload(
        timestamp = Instant.now(),
        legacyTransactionId = 1003,
        requestId = UUID.randomUUID(),
        caseloadId = "DTI",
        requestTypeIdentifier = "NewSyncType",
        synchronizedTransactionId = UUID.randomUUID(),
        body = """{"new": "data"}""",
        transactionType = "TEST",
        transactionTimestamp = Instant.now(),
      )

      val savedPayload = nomisSyncPayloadRepository.save(newPayload)
      entityManager.flush()
      entityManager.clear()

      assertThat(savedPayload.id).isNotNull()
      val retrievedPayload = nomisSyncPayloadRepository.findById(savedPayload.id!!).orElse(null)
      assertThat(retrievedPayload).isNotNull()
      assertThat(retrievedPayload?.body).isEqualTo(newPayload.body)
      assertThat(retrievedPayload?.timestamp).isCloseTo(newPayload.timestamp, Assertions.byLessThan(50, ChronoUnit.MILLIS))
    }
  }

  @Nested
  @DisplayName("findByRequestId")
  inner class FindByRequestId {
    @Test
    fun `should find payload by request ID`() {
      val found = nomisSyncPayloadRepository.findByRequestId(payload2.requestId)
      assertThat(found?.requestId).isEqualTo(payload2.requestId)
    }

    @Test
    fun `should return null if request ID not found`() {
      assertThat(nomisSyncPayloadRepository.findByRequestId(UUID.randomUUID())).isNull()
    }
  }

  @Nested
  @DisplayName("findFirstByLegacyTransactionIdOrderByTimestampDesc")
  inner class FindByLegacyTransactionId {
    @Test
    fun `should find the latest payload by legacy transaction ID`() {
      val found = nomisSyncPayloadRepository.findFirstByLegacyTransactionIdOrderByTimestampDesc(payload1.legacyTransactionId!!)
      assertThat(found?.requestId).isEqualTo(payload1.requestId)
    }
  }

  @Nested
  @DisplayName("findLatestByTransactionTimestampBetweenAndRequestTypeIdentifier")
  inner class FindLatestByTransactionTimestampBetweenAndRequestTypeIdentifier {
    @Test
    fun `should find the latest payloads within the date range and by request type`() {
      val pageable: Pageable = PageRequest.of(0, 10)
      val found = nomisSyncPayloadRepository.findLatestByTransactionTimestampBetweenAndRequestTypeIdentifier(
        Instant.now().minus(10, ChronoUnit.DAYS),
        Instant.now(),
        requestType1,
        pageable,
      )
      assertThat(found.content).hasSize(2)
      assertThat(found.content.map { it.requestId }).containsExactlyInAnyOrder(payload1.requestId, payload2.requestId)
    }
  }

  @Nested
  @DisplayName("findAll")
  inner class FindAll {
    @Test
    fun `should retrieve all payloads`() {
      val found = nomisSyncPayloadRepository.findAll()
      assertThat(found).hasSize(initialPayloadCount)
    }
  }
}
