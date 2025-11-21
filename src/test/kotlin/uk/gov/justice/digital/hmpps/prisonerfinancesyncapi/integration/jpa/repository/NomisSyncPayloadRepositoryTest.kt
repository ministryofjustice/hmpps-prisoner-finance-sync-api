package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.jpa.repository

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.util.RepositoryTest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RepositoryTest
class NomisSyncPayloadRepositoryTest @Autowired constructor(
  val entityManager: TestEntityManager,
  val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
) {

  private val requestType1 = "SyncOffenderTransaction"
  private lateinit var payload1: NomisSyncPayload
  private lateinit var payload2: NomisSyncPayload
  private lateinit var payload3: NomisSyncPayload
  private lateinit var payload4: NomisSyncPayload

  private val initialPayloadCount = 4
  private val uniqueInitialPayloadsForRequestType = 2

  @BeforeEach
  fun setup() {
    nomisSyncPayloadRepository.deleteAll()
    entityManager.flush()

    val now = Instant.now()
    val synchronizedTransactionId1 = UUID.randomUUID()
    val synchronizedTransactionId2 = UUID.randomUUID()

    payload1 = NomisSyncPayload(
      timestamp = now.minus(10, ChronoUnit.MINUTES),
      legacyTransactionId = 1001,
      requestId = UUID.randomUUID(),
      caseloadId = "MDI",
      requestTypeIdentifier = requestType1,
      synchronizedTransactionId = synchronizedTransactionId1,
      body = """{"transactionId":1001,"caseloadId":"MDI","offenderId":123,"eventType":"SyncOffenderTransaction"}""",
      transactionTimestamp = now.minus(5, ChronoUnit.DAYS),
    )
    entityManager.persistAndFlush(payload1)

    payload2 = NomisSyncPayload(
      timestamp = now.minus(5, ChronoUnit.MINUTES),
      legacyTransactionId = 1002,
      requestId = UUID.randomUUID(),
      caseloadId = "LEI",
      requestTypeIdentifier = requestType1,
      synchronizedTransactionId = synchronizedTransactionId2,
      body = """{"transactionId":1003,"caseloadId":"LEI","offenderId":456,"eventType":"SyncOffenderTransaction"}""",
      transactionTimestamp = now.minus(3, ChronoUnit.DAYS),
    )
    entityManager.persistAndFlush(payload2)

    payload3 = NomisSyncPayload(
      timestamp = now.minus(15, ChronoUnit.MINUTES),
      legacyTransactionId = 1001,
      requestId = UUID.randomUUID(),
      caseloadId = "MDI",
      requestTypeIdentifier = requestType1,
      synchronizedTransactionId = synchronizedTransactionId1,
      body = """{"transactionId":1001,"caseloadId":"MDI","offenderId":123,"eventType":"SyncOffenderTransaction"}""",
      transactionTimestamp = now.minus(5, ChronoUnit.DAYS),
    )
    entityManager.persistAndFlush(payload3)

    payload4 = NomisSyncPayload(
      timestamp = now.minus(2, ChronoUnit.MINUTES),
      legacyTransactionId = 1004,
      requestId = UUID.randomUUID(),
      caseloadId = "MDI",
      requestTypeIdentifier = "AnotherSyncType",
      synchronizedTransactionId = UUID.randomUUID(),
      body = """{"transactionId":1004,"caseloadId":"MDI","offenderId":789,"eventType":"AnotherSyncType"}""",
      transactionTimestamp = now.minus(1, ChronoUnit.DAYS),
    )
    entityManager.persistAndFlush(payload4)
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
        body = """{"new":"data"}""",
        transactionTimestamp = Instant.now(),
      )

      val savedPayload = nomisSyncPayloadRepository.save(newPayload)
      entityManager.flush()
      entityManager.clear()

      assertThat(savedPayload.id).isNotNull()
      val retrievedPayload = nomisSyncPayloadRepository.findById(savedPayload.id!!).orElse(null)
      assertThat(retrievedPayload).isNotNull()

      assertThat(retrievedPayload?.id).isEqualTo(savedPayload.id)
      assertThat(retrievedPayload?.legacyTransactionId).isEqualTo(savedPayload.legacyTransactionId)
      assertThat(retrievedPayload?.requestId).isEqualTo(savedPayload.requestId)
      assertThat(retrievedPayload?.caseloadId).isEqualTo(savedPayload.caseloadId)
      assertThat(retrievedPayload?.requestTypeIdentifier).isEqualTo(savedPayload.requestTypeIdentifier)
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
      assertThat(found).isEqualTo(payload2)
    }

    @Test
    fun `should return null if request ID not found`() {
      val found = nomisSyncPayloadRepository.findByRequestId(UUID.randomUUID())
      assertThat(found).isNull()
    }
  }

  @Nested
  @DisplayName("findFirstByLegacyTransactionIdOrderByTimestampDesc")
  inner class FindByLegacyTransactionId {
    @Test
    fun `should find the latest payload by legacy transaction ID`() {
      val found = nomisSyncPayloadRepository.findFirstByLegacyTransactionIdOrderByTimestampDesc(payload1.legacyTransactionId!!)
      assertThat(found).isEqualTo(payload1)
    }

    @Test
    fun `should return null if legacy transaction ID not found`() {
      val found = nomisSyncPayloadRepository.findFirstByLegacyTransactionIdOrderByTimestampDesc(9999)
      assertThat(found).isNull()
    }
  }

  @Nested
  @DisplayName("findFirstBySynchronizedTransactionIdOrderByTimestampDesc")
  inner class FindFirstBySynchronizedTransactionIdOrderByTimestampDesc {
    @Test
    fun `should find the latest payload by synchronized transaction ID`() {
      val found = nomisSyncPayloadRepository.findFirstBySynchronizedTransactionIdOrderByTimestampDesc(payload1.synchronizedTransactionId)
      assertThat(found).isEqualTo(payload1)
    }

    @Test
    fun `should return null if synchronized transaction ID not found`() {
      val found = nomisSyncPayloadRepository.findFirstBySynchronizedTransactionIdOrderByTimestampDesc(UUID.randomUUID())
      assertThat(found).isNull()
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
      assertThat(found.content).containsExactlyInAnyOrder(payload1, payload2)
      assertThat(found.totalElements).isEqualTo(2)
    }

    @Test
    fun `should return empty list if no payloads within the date range`() {
      val pageable: Pageable = PageRequest.of(0, 10)
      val found = nomisSyncPayloadRepository.findLatestByTransactionTimestampBetweenAndRequestTypeIdentifier(
        Instant.now().minus(20, ChronoUnit.DAYS),
        Instant.now().minus(15, ChronoUnit.DAYS),
        requestType1,
        pageable,
      )
      assertThat(found.content).isEmpty()
      assertThat(found.totalElements).isEqualTo(0)
    }

    @Test
    fun `should return empty list if no payloads with the request type`() {
      val pageable: Pageable = PageRequest.of(0, 10)
      val found = nomisSyncPayloadRepository.findLatestByTransactionTimestampBetweenAndRequestTypeIdentifier(
        Instant.now().minus(10, ChronoUnit.DAYS),
        Instant.now(),
        "NonExistentType",
        pageable,
      )
      assertThat(found.content).isEmpty()
      assertThat(found.totalElements).isEqualTo(0)
    }

    @Test
    fun `should only return the latest payload for each synchronizedTransactionId`() {
      val synchronizedTransactionId = UUID.randomUUID()
      val olderPayload = NomisSyncPayload(
        timestamp = Instant.now().minus(30, ChronoUnit.MINUTES),
        legacyTransactionId = 1005,
        requestId = UUID.randomUUID(),
        caseloadId = "XYZ",
        requestTypeIdentifier = requestType1,
        synchronizedTransactionId = synchronizedTransactionId,
        body = """{"transactionId":1005,"caseloadId":"XYZ","offenderId":901,"eventType":"SyncOffenderTransaction"}""",
        transactionTimestamp = Instant.now().minus(2, ChronoUnit.DAYS),
      )
      entityManager.persistAndFlush(olderPayload)

      val newerPayload = NomisSyncPayload(
        timestamp = Instant.now().minus(10, ChronoUnit.MINUTES),
        legacyTransactionId = 1005,
        requestId = UUID.randomUUID(),
        caseloadId = "XYZ",
        requestTypeIdentifier = requestType1,
        synchronizedTransactionId = synchronizedTransactionId,
        body = """{"transactionId":1005,"caseloadId":"XYZ","offenderId":901,"eventType":"SyncOffenderTransaction", "updated": true}""",
        transactionTimestamp = Instant.now().minus(2, ChronoUnit.DAYS),
      )
      entityManager.persistAndFlush(newerPayload)

      val pageable: Pageable = PageRequest.of(0, 10)
      val found = nomisSyncPayloadRepository.findLatestByTransactionTimestampBetweenAndRequestTypeIdentifier(
        Instant.now().minus(10, ChronoUnit.DAYS),
        Instant.now(),
        requestType1,
        pageable,
      )
      assertThat(found.content).hasSize(3)
      assertThat(found.content).containsExactlyInAnyOrder(payload1, payload2, newerPayload)
      assertThat(found.totalElements).isEqualTo(3)
    }

    @Test
    fun `should paginate results correctly`() {
      val newPayloadCount = 25
      val totalUniquePayloads = uniqueInitialPayloadsForRequestType + newPayloadCount

      val pageSize = 10
      val firstPageNumber = 0
      val secondPageNumber = 1
      val lastPageNumber = 2

      val expectedFirstPageSize = 10
      val expectedSecondPageSize = 10
      val expectedLastPageSize = totalUniquePayloads - (expectedFirstPageSize + expectedSecondPageSize)

      val payloads = (1..newPayloadCount).map {
        NomisSyncPayload(
          timestamp = Instant.now().minus(it.toLong(), ChronoUnit.MINUTES),
          legacyTransactionId = 1000L + it,
          requestId = UUID.randomUUID(),
          caseloadId = "MDI",
          requestTypeIdentifier = requestType1,
          synchronizedTransactionId = UUID.randomUUID(),
          body = """{"transactionId":100$it,"caseloadId":"MDI","offenderId":123,"eventType":"SyncOffenderTransaction"}""",
          transactionTimestamp = Instant.now().minus(it.toLong(), ChronoUnit.DAYS),
        )
      }
      payloads.forEach { entityManager.persistAndFlush(it) }

      // Get first page of 10
      val pageable1: Pageable = PageRequest.of(firstPageNumber, pageSize)
      val foundPage1 = nomisSyncPayloadRepository.findLatestByTransactionTimestampBetweenAndRequestTypeIdentifier(
        Instant.now().minus(30, ChronoUnit.DAYS),
        Instant.now(),
        requestType1,
        pageable1,
      )
      assertThat(foundPage1.content).hasSize(expectedFirstPageSize)
      assertThat(foundPage1.totalElements).isEqualTo(totalUniquePayloads.toLong())
      assertThat(foundPage1.totalPages).isEqualTo(3)
      assertThat(foundPage1.number).isEqualTo(firstPageNumber)

      // Get second page of 10
      val pageable2: Pageable = PageRequest.of(secondPageNumber, pageSize)
      val foundPage2 = nomisSyncPayloadRepository.findLatestByTransactionTimestampBetweenAndRequestTypeIdentifier(
        Instant.now().minus(30, ChronoUnit.DAYS),
        Instant.now(),
        requestType1,
        pageable2,
      )
      assertThat(foundPage2.content).hasSize(expectedSecondPageSize)
      assertThat(foundPage2.totalElements).isEqualTo(totalUniquePayloads.toLong())
      assertThat(foundPage2.totalPages).isEqualTo(3)
      assertThat(foundPage2.number).isEqualTo(secondPageNumber)

      // Get last page
      val pageable3: Pageable = PageRequest.of(lastPageNumber, pageSize)
      val foundPage3 = nomisSyncPayloadRepository.findLatestByTransactionTimestampBetweenAndRequestTypeIdentifier(
        Instant.now().minus(30, ChronoUnit.DAYS),
        Instant.now(),
        requestType1,
        pageable3,
      )
      assertThat(foundPage3.content).hasSize(expectedLastPageSize)
      assertThat(foundPage3.totalElements).isEqualTo(totalUniquePayloads.toLong())
      assertThat(foundPage3.totalPages).isEqualTo(3)
      assertThat(foundPage3.number).isEqualTo(lastPageNumber)
    }
  }

  @Nested
  @DisplayName("findAll")
  inner class FindAll {
    @Test
    fun `should retrieve all payloads`() {
      val found = nomisSyncPayloadRepository.findAll()
      assertThat(found).hasSize(initialPayloadCount)
      assertThat(found).containsExactlyInAnyOrder(payload1, payload2, payload3, payload4)
    }
  }
}
