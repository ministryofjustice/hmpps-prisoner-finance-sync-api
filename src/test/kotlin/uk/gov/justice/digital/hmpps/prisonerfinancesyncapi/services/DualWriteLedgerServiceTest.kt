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
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DualWriteLedgerServiceTest {

  @Mock
  private lateinit var internalLedger: LedgerService

  @Mock
  private lateinit var generalLedger: LedgerService

  private lateinit var listAppender: ListAppender<ILoggingEvent>
  private lateinit var dualWriteService: DualWriteLedgerService

  private val matchingPrisonerId = "A1234AA"
  private val nonMatchingPrisonerId = "B9876BB"

  private val logger = LoggerFactory.getLogger(DualWriteLedgerService::class.java) as Logger

  @BeforeEach
  fun setup() {
    listAppender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(listAppender)
  }

  @AfterEach
  fun tearDown() {
    logger.detachAppender(listAppender)
  }

  @Test
  fun `should log configuration on startup`() {
    dualWriteService = DualWriteLedgerService(internalLedger, generalLedger, true, "TEST_ID")

    val logs = listAppender.list.map { it.formattedMessage }
    assertThat(logs).anyMatch {
      it.contains("General Ledger Dual Write Service initialized. Enabled: true. Test Prisoner ID: TEST_ID")
    }
  }

  @Nested
  @DisplayName("syncOffenderTransaction")
  inner class SyncOffenderTransaction {

    @Test
    fun `should only call the internal ledger when feature flag is disabled`() {
      dualWriteService = DualWriteLedgerService(internalLedger, generalLedger, false, matchingPrisonerId)

      val request = createRequest(matchingPrisonerId)
      val expectedUuid = UUID.randomUUID()

      whenever(internalLedger.syncOffenderTransaction(request)).thenReturn(expectedUuid)

      val result = dualWriteService.syncOffenderTransaction(request)

      assertThat(result).isEqualTo(expectedUuid)
      verify(internalLedger).syncOffenderTransaction(request)
      verify(generalLedger, never()).syncOffenderTransaction(any())
    }

    @Test
    fun `should call both ledgers when feature flag is enabled and prisoner ID matches`() {
      dualWriteService = DualWriteLedgerService(internalLedger, generalLedger, true, matchingPrisonerId)

      val request = createRequest(matchingPrisonerId)
      val expectedUuid = UUID.randomUUID()

      whenever(internalLedger.syncOffenderTransaction(request)).thenReturn(expectedUuid)

      val result = dualWriteService.syncOffenderTransaction(request)

      assertThat(result).isEqualTo(expectedUuid)

      val inOrder = inOrder(internalLedger, generalLedger)
      inOrder.verify(internalLedger).syncOffenderTransaction(request)
      inOrder.verify(generalLedger).syncOffenderTransaction(request)
    }

    @Test
    fun `should skip general ledger when feature flag is enabled but prisoner id does not match`() {
      dualWriteService = DualWriteLedgerService(internalLedger, generalLedger, true, matchingPrisonerId)

      val request = createRequest(nonMatchingPrisonerId)
      val expectedUuid = UUID.randomUUID()

      whenever(internalLedger.syncOffenderTransaction(request)).thenReturn(expectedUuid)

      val result = dualWriteService.syncOffenderTransaction(request)

      assertThat(result).isEqualTo(expectedUuid)

      verify(internalLedger).syncOffenderTransaction(request)
      verify(generalLedger, never()).syncOffenderTransaction(any())
    }

    @Test
    fun `should suppress exception from general ledger and log an error when flag is enabled and id matches`() {
      dualWriteService = DualWriteLedgerService(internalLedger, generalLedger, true, matchingPrisonerId)

      val request = createRequest(matchingPrisonerId)
      val expectedUuid = UUID.randomUUID()
      val transactionId = 12345L

      whenever(internalLedger.syncOffenderTransaction(request)).thenReturn(expectedUuid)
      whenever(generalLedger.syncOffenderTransaction(request)).thenThrow(RuntimeException("API Down"))

      val result = dualWriteService.syncOffenderTransaction(request)

      assertThat(result).isEqualTo(expectedUuid)

      verify(internalLedger).syncOffenderTransaction(request)
      verify(generalLedger).syncOffenderTransaction(request)

      val logs = listAppender.list.map { it.formattedMessage }
      assertThat(logs).anyMatch {
        it.contains("Failed to sync Offender Transaction $transactionId to General Ledger")
      }
    }

    @Test
    fun `should throw exception if internal ledger throws`() {
      dualWriteService = DualWriteLedgerService(internalLedger, generalLedger, true, matchingPrisonerId)

      val request = createRequest(matchingPrisonerId)

      whenever(internalLedger.syncOffenderTransaction(request)).thenThrow(RuntimeException("DB Error"))

      assertThatThrownBy {
        dualWriteService.syncOffenderTransaction(request)
      }.isInstanceOf(RuntimeException::class.java)
        .hasMessage("DB Error")

      verify(generalLedger, never()).syncOffenderTransaction(any())
    }

    private fun createRequest(offenderId: String): SyncOffenderTransactionRequest {
      val offenderTx = mock<OffenderTransaction>()
      lenient().whenever(offenderTx.offenderDisplayId).thenReturn(offenderId)

      val request = mock<SyncOffenderTransactionRequest>()
      lenient().whenever(request.offenderTransactions).thenReturn(listOf(offenderTx))
      lenient().whenever(request.transactionId).thenReturn(12345L)
      return request
    }
  }

  @Nested
  @DisplayName("syncGeneralLedgerTransaction")
  inner class SyncGeneralLedgerTransaction {
    @Test
    fun `should only call internal ledger (feature flag ignored for GL Transactions)`() {
      dualWriteService = DualWriteLedgerService(internalLedger, generalLedger, true, matchingPrisonerId)
      val request = mock<SyncGeneralLedgerTransactionRequest>()
      val expectedUuid = UUID.randomUUID()

      whenever(internalLedger.syncGeneralLedgerTransaction(request)).thenReturn(expectedUuid)

      val result = dualWriteService.syncGeneralLedgerTransaction(request)

      assertThat(result).isEqualTo(expectedUuid)
      verify(internalLedger).syncGeneralLedgerTransaction(request)
      verify(generalLedger, never()).syncGeneralLedgerTransaction(any())
    }

    @Test
    fun `should throw exception if internal ledger fails`() {
      dualWriteService = DualWriteLedgerService(internalLedger, generalLedger, true, matchingPrisonerId)
      val request = mock<SyncGeneralLedgerTransactionRequest>()

      whenever(internalLedger.syncGeneralLedgerTransaction(request)).thenThrow(RuntimeException("Internal DB Error"))

      assertThatThrownBy {
        dualWriteService.syncGeneralLedgerTransaction(request)
      }.isInstanceOf(RuntimeException::class.java)
        .hasMessage("Internal DB Error")
    }
  }
}
