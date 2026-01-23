package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.springframework.data.domain.PageImpl
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.audit.AuditHistoryController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadDetail
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadSummary
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.AuditHistoryService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.AuditHistoryServiceTest
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@DisplayName("PayloadsViewController")
class AuditHistoryControllerTest {

  fun createNomisSyncPayloadDto(
    legacyTransactionId: Long?,
    caseloadId: String?,
    requestTypeIdentifier: String? = null,
    transactionTimestamp: Instant? = Instant.now(),
    timestamp: Instant = Instant.now(),
    synchronizedTransactionId: UUID = UUID.randomUUID(),
    requestId: UUID = UUID.randomUUID(),
  ): NomisSyncPayloadSummary = AuditHistoryServiceTest.TestNomisSyncPayloadSummary(
    timestamp = timestamp,
    legacyTransactionId = legacyTransactionId,
    synchronizedTransactionId = synchronizedTransactionId,
    requestId = requestId,
    caseloadId = caseloadId,
    requestTypeIdentifier = requestTypeIdentifier,
    transactionTimestamp = transactionTimestamp,
  )

  @Mock
  private lateinit var auditHistoryService: AuditHistoryService

  @InjectMocks
  private lateinit var auditHistoryController: AuditHistoryController

  private val caseloadId = "TST"

  @Nested
  @DisplayName("getPayloadsByCaseload")
  inner class GetPayloadsByCaseloadTest {

    @Test
    fun `should return a list of payloads when repository returns payloads`() {
      val startDate = LocalDate.now().minus(30, ChronoUnit.DAYS)
      val endDate = LocalDate.now()
      val page = 1
      val size = 10

      val payloads = listOf(createNomisSyncPayloadDto(1, caseloadId))
      `when`(auditHistoryService.getMatchingPayloads(caseloadId, null, startDate, endDate, page, size))
        .thenReturn(PageImpl(payloads))

      val response = auditHistoryController.getMatchingPayloads(caseloadId, null, startDate, endDate, page, size)

      assertThat(response.body?.content).hasSize(1)
      assertThat(response.body?.content?.get(0)?.legacyTransactionId).isEqualTo(1L)
      verify(auditHistoryService).getMatchingPayloads(caseloadId, null, startDate, endDate, page, size)
      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(PageImpl(payloads))
    }
  }

  @Nested
  @DisplayName("getPayloadByRequestId")
  inner class GetPayloadByRequestId {

    @Test
    fun `should return a payload when repository has a payload`() {
      val requestId = UUID.randomUUID()
      val legacyTransactionId = 1L
      val payload = NomisSyncPayloadDetail(
        timestamp = Instant.now(),
        legacyTransactionId = 1001,
        requestId = requestId,
        caseloadId = "MDI",
        requestTypeIdentifier = "TEST",
        synchronizedTransactionId = UUID.randomUUID(),
        body = """{"transactionId":1001,"caseloadId":"MDI","offenderId":123,"eventType":"SyncOffenderTransaction"}""",
        transactionTimestamp = Instant.now(),
      )
      `when`(auditHistoryService.getPayloadBodyByRequestId(requestId))
        .thenReturn(payload)

      val response = auditHistoryController.getPayloadByRequestId(requestId)

      assertThat(response.body).isEqualTo(payload)
      verify(auditHistoryService).getPayloadBodyByRequestId(requestId)
      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `should throw NotFound when no payload is found`() {
      val requestId = UUID.randomUUID()
      `when`(auditHistoryService.getPayloadBodyByRequestId(requestId))
        .thenReturn(null)

      val response = auditHistoryController.getPayloadByRequestId(requestId)

      assertThat(response.body).isNull()
      verify(auditHistoryService).getPayloadBodyByRequestId(requestId)
      assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
  }
}
