package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.audit

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.CursorPage
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadDetail
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadSummary
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.AuditHistoryService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.AuditHistoryServiceTest
import java.time.Instant
import java.time.LocalDate
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
    id = 1L,
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
  @DisplayName("getMatchingPayloads")
  inner class GetMatchingPayloadsTest {

    @Test
    fun `should return a list of payloads when service returns payloads`() {
      val startDate = LocalDate.now().minusDays(30)
      val endDate = LocalDate.now()
      val cursor = "dGhlLWN1cnNvci1zdHJpbmc"
      val size = 10

      val payloads = listOf(createNomisSyncPayloadDto(1, caseloadId))
      val cursorPage = CursorPage(payloads, "next-cursor", 1, size)

      Mockito.`when`(auditHistoryService.getMatchingPayloads(caseloadId, null, startDate, endDate, cursor, size))
        .thenReturn(cursorPage)

      val response = auditHistoryController.getMatchingPayloads(caseloadId, null, startDate, endDate, cursor, size)

      Assertions.assertThat(response.body?.content).hasSize(1)
      Assertions.assertThat(response.body?.content?.get(0)?.legacyTransactionId).isEqualTo(1L)
      verify(auditHistoryService).getMatchingPayloads(caseloadId, null, startDate, endDate, cursor, size)
      Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      Assertions.assertThat(response.body).isEqualTo(cursorPage)
    }
  }

  @Nested
  @DisplayName("getPayloadByRequestId")
  inner class GetPayloadByRequestId {

    @Test
    fun `should return a payload when service has a payload`() {
      val requestId = UUID.randomUUID()
      val payload = NomisSyncPayloadDetail(
        timestamp = Instant.now(),
        legacyTransactionId = 1001,
        requestId = requestId,
        caseloadId = "MDI",
        requestTypeIdentifier = "TEST",
        synchronizedTransactionId = UUID.randomUUID(),
        transactionType = "TEST",
        body = """{"transactionId":1001,"caseloadId":"MDI","offenderId":123,"eventType":"SyncOffenderTransaction"}""",
        transactionTimestamp = Instant.now(),
      )
      Mockito.`when`(auditHistoryService.getPayloadBodyByRequestId(requestId))
        .thenReturn(payload)

      val response = auditHistoryController.getPayloadByRequestId(requestId)

      Assertions.assertThat(response.body).isEqualTo(payload)
      verify(auditHistoryService).getPayloadBodyByRequestId(requestId)
      Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `should return 404 when no payload is found`() {
      val requestId = UUID.randomUUID()
      Mockito.`when`(auditHistoryService.getPayloadBodyByRequestId(requestId))
        .thenReturn(null)

      val response = auditHistoryController.getPayloadByRequestId(requestId)

      Assertions.assertThat(response.body).isNull()
      verify(auditHistoryService).getPayloadBodyByRequestId(requestId)
      Assertions.assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
  }
}
