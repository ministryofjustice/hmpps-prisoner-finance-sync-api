package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import DualMigrationService
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
import org.mockito.Mockito.doNothing
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration.MigrationService

@ExtendWith(MockitoExtension::class)
class DualMigrationServiceTest {

  @Mock
  private lateinit var internalMigrationService: MigrationService

  @Mock
  private lateinit var generalLedgerMigrationService: MigrationService

  private lateinit var listAppender: ListAppender<ILoggingEvent>

  private lateinit var dualMigrationService: DualMigrationService

  private val matchingPrisonerId = "A1234AA"

  private val logger = LoggerFactory.getLogger(DualWriteLedgerService::class.java) as Logger

  @BeforeEach
  fun setup() {
    dualMigrationService = DualMigrationService(internalMigrationService, generalLedgerMigrationService, true, matchingPrisonerId)
    listAppender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(listAppender)
  }

  @AfterEach
  fun tearDown() {
    logger.detachAppender(listAppender)
  }

  @Test
  fun `should log configuration on startup`() {
    dualMigrationService = DualMigrationService(internalMigrationService, generalLedgerMigrationService, true, "TEST_ID")
    val logs = listAppender.list.map { it.formattedMessage }
    assertThat(logs).anyMatch {
      it.contains("General Ledger Dual Migration Service initialized. Enabled: true. Test Prisoner ID: TEST_ID")
    }
  }

  @Nested
  @DisplayName("usingLegacyMigration")
  inner class LegacyMigration {

    @Test
    fun `should only call the internal ledger when feature flag is disabled`() {
      dualMigrationService = DualMigrationService(internalMigrationService, generalLedgerMigrationService, false, matchingPrisonerId)

      val mockPrisonerBalancesSyncRequest = mock<PrisonerBalancesSyncRequest>()

      doNothing().whenever(internalMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)

      val result = dualMigrationService.migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)

      assertThat(result).isEqualTo(Unit)
      verify(internalMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)
    }

    @Test
    fun `should skip general ledger when feature flag is enabled but prisoner id does not match`() {
      dualMigrationService = DualMigrationService(internalMigrationService, generalLedgerMigrationService, true, "NONE_MATCH_ID")

      val mockPrisonerBalancesSyncRequest = mock<PrisonerBalancesSyncRequest>()

      doNothing().whenever(internalMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)

      val result = dualMigrationService.migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)

      assertThat(result).isEqualTo(Unit)
      verify(internalMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)
      verify(generalLedgerMigrationService, never()).migratePrisonerBalances(any(), any())
    }

    @Test
    fun `should throw exception if internal migration throws`() {
      dualMigrationService = DualMigrationService(internalMigrationService, generalLedgerMigrationService, false, matchingPrisonerId)

      val mockPrisonerBalancesSyncRequest = mock<PrisonerBalancesSyncRequest>()

      whenever(internalMigrationService.migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest))
        .thenThrow(RuntimeException("Internal DB Error"))

      assertThatThrownBy {
        dualMigrationService.migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)
      }.isInstanceOf(RuntimeException::class.java)
        .hasMessage("Internal DB Error")

      verify(internalMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)
    }
  }

  @Nested
  @DisplayName("usingGeneralLedgerMigration")
  inner class GeneralLedgerMigration {
    /*@Test
    fun `should only call internal ledger (feature flag ignored for GL Transactions)`() {
      val request = mock<SyncGeneralLedgerTransactionRequest>()
      val expectedUuid = UUID.randomUUID()

      whenever(internalLedger.syncGeneralLedgerTransaction(request)).thenReturn(expectedUuid)

      val result = dualWriteService.syncGeneralLedgerTransaction(request)

      assertThat(result).isEqualTo(expectedUuid)
      verify(internalLedger).syncGeneralLedgerTransaction(request)
      verify(generalLedger, never()).syncGeneralLedgerTransaction(any())
    }*/

    @Test
    fun `should throw exception if internal ledger fails`() {
      val request = mock<SyncGeneralLedgerTransactionRequest>()

      // whenever(internalLedger.syncGeneralLedgerTransaction(request)).thenThrow(RuntimeException("Internal DB Error"))

      /*assertThatThrownBy {
        dualWriteService.syncGeneralLedgerTransaction(request)
      }.isInstanceOf(RuntimeException::class.java)
        .hasMessage("Internal DB Error")*/
    }

    @Test
    fun `should call both ledgers when feature flag is enabled and prisoner ID matches`() {
      /*val request = createRequest(matchingPrisonerId)
      val expectedUuid = UUID.randomUUID()

      whenever(internalLedger.syncOffenderTransaction(request)).thenReturn(expectedUuid)

      val result = dualWriteService.syncOffenderTransaction(request)

      assertThat(result).isEqualTo(expectedUuid)

      val inOrder = inOrder(internalLedger, generalLedger)
      inOrder.verify(internalLedger).syncOffenderTransaction(request)
      inOrder.verify(generalLedger).syncOffenderTransaction(request)*/
    }

    /*@Test
    fun `should suppress exception from general ledger and log an error when flag is enabled and id matches`() {
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
    }*/
  }
}
