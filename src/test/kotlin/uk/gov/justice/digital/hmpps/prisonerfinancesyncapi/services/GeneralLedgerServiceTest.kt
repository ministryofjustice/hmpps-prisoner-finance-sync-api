package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GeneralLedgerServiceTest {

  @Mock
  private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @InjectMocks
  private lateinit var generalLedgerService: GeneralLedgerService

  private lateinit var listAppender: ListAppender<ILoggingEvent>

  private val logger = LoggerFactory.getLogger(GeneralLedgerService::class.java) as Logger

  @BeforeEach
  fun setup() {
    listAppender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(listAppender)
  }

  @AfterEach
  fun tearDown() {
    logger.detachAppender(listAppender)
  }

  @Nested
  @DisplayName("syncOffenderTransaction")
  inner class SyncOffenderTransaction {

    private val offenderDisplayId = "A1234AA"

    @Test
    fun `should call general ledger api and log message when account found`() {
      val request = createOffenderRequest(offenderDisplayId)
      val accountUuid = UUID.randomUUID()

      val mockResponse = GlAccountResponse(
        id = accountUuid,
        reference = offenderDisplayId,
        createdAt = LocalDateTime.now(),
        createdBy = "SYSTEM",
        subAccounts = emptyList(),
      )

      whenever(generalLedgerApiClient.findAccountByReference(offenderDisplayId)).thenReturn(mockResponse)

      val result = generalLedgerService.syncOffenderTransaction(request)

      verify(generalLedgerApiClient).findAccountByReference(offenderDisplayId)
      assertThat(result).isNotNull()

      val logs = listAppender.list.map { it.formattedMessage }
      assertThat(logs).anyMatch { it.contains("General Ledger account found for '$offenderDisplayId' (UUID: $accountUuid)") }
    }

    @Test
    fun `should call general ledger api and log message when account not found`() {
      val request = createOffenderRequest(offenderDisplayId)
      whenever(generalLedgerApiClient.findAccountByReference(offenderDisplayId)).thenReturn(null)

      val result = generalLedgerService.syncOffenderTransaction(request)

      verify(generalLedgerApiClient).findAccountByReference(offenderDisplayId)
      assertThat(result).isNotNull()

      val logs = listAppender.list.map { it.formattedMessage }
      assertThat(logs).contains("General Ledger account not found for '$offenderDisplayId'")
    }

    @Test
    fun `should propagate client exceptions`() {
      val request = createOffenderRequest(offenderDisplayId)
      val expectedError = RuntimeException("Network Error")

      whenever(generalLedgerApiClient.findAccountByReference(offenderDisplayId)).thenThrow(expectedError)

      assertThatThrownBy {
        generalLedgerService.syncOffenderTransaction(request)
      }.isEqualTo(expectedError)
    }

    private fun createOffenderRequest(offenderDisplayId: String): SyncOffenderTransactionRequest {
      val offenderTx = mock<OffenderTransaction>()
      whenever(offenderTx.offenderDisplayId).thenReturn(offenderDisplayId)

      val request = mock<SyncOffenderTransactionRequest>()
      whenever(request.offenderTransactions).thenReturn(listOf(offenderTx))
      return request
    }
  }

  @Nested
  @DisplayName("syncGeneralLedgerTransaction")
  inner class SyncGeneralLedgerTransaction {

    @Test
    fun `should throw NotImplementedError`() {
      val request = mock<SyncGeneralLedgerTransactionRequest>()

      assertThatThrownBy {
        generalLedgerService.syncGeneralLedgerTransaction(request)
      }.isInstanceOf(NotImplementedError::class.java)
        .hasMessageContaining("not yet supported")
    }
  }
}
