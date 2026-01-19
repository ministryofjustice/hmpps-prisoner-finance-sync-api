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
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.audit.HistoryController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.NomisSyncPayloadDto
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.PayloadTransactionDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.AuditHistoryService
import java.time.Instant
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
  ): NomisSyncPayloadDto = NomisSyncPayloadDto(
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
  private lateinit var historyController: HistoryController

  private val caseloadId = "TST"

  @Nested
  @DisplayName("getPayloadsByCaseload")
  inner class GetPayloadsByCaseloadTest {

    @Test
    fun `should return a list of payloads when repository returns payloads`() {
      val startDate = Instant.now().minus(30, ChronoUnit.DAYS)
      val endDate = Instant.now()
      val page = 1
      val size = 10

      val payloads = listOf(createNomisSyncPayloadDto(1, caseloadId))
      `when`(auditHistoryService.getPayloadsByCaseloadAndDateRange(caseloadId, startDate, endDate, page, size)).thenReturn(payloads)

      val response = historyController.getPayloadsByCaseloadAndDateRange(caseloadId, startDate, endDate, page, size)

      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(PayloadTransactionDetailsList(items = payloads))
    }
  }
}
