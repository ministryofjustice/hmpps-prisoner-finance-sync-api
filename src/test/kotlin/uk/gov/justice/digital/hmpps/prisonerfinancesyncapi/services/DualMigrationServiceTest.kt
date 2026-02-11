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
import org.mockito.Mockito.doNothing
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration.DualMigrationService
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

  private val logger = LoggerFactory.getLogger(DualMigrationService::class.java) as Logger

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
    fun `should only call the internal migration service when feature flag is disabled`() {
      dualMigrationService = DualMigrationService(internalMigrationService, generalLedgerMigrationService, false, matchingPrisonerId)

      val mockPrisonerBalancesSyncRequest = mock<PrisonerBalancesSyncRequest>()

      doNothing().whenever(internalMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)

      val result = dualMigrationService.migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)

      assertThat(result).isEqualTo(Unit)
      verify(internalMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)
    }

    @Test
    fun `should skip general ledger migration when feature flag is enabled but prisoner id does not match`() {
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

    @Test
    fun `should only call internal migration service (feature flag ignored for GL balance migration)`() {
      dualMigrationService = DualMigrationService(internalMigrationService, generalLedgerMigrationService, false, matchingPrisonerId)

      val mockGeneralLedgerBalancesSyncRequest = mock<GeneralLedgerBalancesSyncRequest>()

      doNothing().whenever(internalMigrationService).migrateGeneralLedgerBalances(matchingPrisonerId, mockGeneralLedgerBalancesSyncRequest)

      val result = dualMigrationService.migrateGeneralLedgerBalances(matchingPrisonerId, mockGeneralLedgerBalancesSyncRequest)

      assertThat(result).isEqualTo(Unit)
      verify(internalMigrationService).migrateGeneralLedgerBalances(matchingPrisonerId, mockGeneralLedgerBalancesSyncRequest)
    }
  }

  @Nested
  @DisplayName("usingGeneralLedgerMigration")
  inner class GeneralLedgerMigration {

    @Test
    fun `should log error when migrateGeneralLedgerBalances throws exception in general ledger migration`() {
      val mockGeneralLedgerBalancesSyncRequest = mock<GeneralLedgerBalancesSyncRequest>()

      doNothing().whenever(internalMigrationService).migrateGeneralLedgerBalances(matchingPrisonerId, mockGeneralLedgerBalancesSyncRequest)

      whenever(generalLedgerMigrationService.migrateGeneralLedgerBalances(matchingPrisonerId, mockGeneralLedgerBalancesSyncRequest))
        .thenThrow(RuntimeException("Internal DB Error"))

      val result = dualMigrationService.migrateGeneralLedgerBalances(matchingPrisonerId, mockGeneralLedgerBalancesSyncRequest)

      val logs = listAppender.list.map { it.formattedMessage }
      assertThat(logs).anyMatch {
        it.contains("Failed to migrate general ledger balances for prisoner $matchingPrisonerId to General Ledger")
      }

      assertThat(result).isEqualTo(Unit)

      verify(internalMigrationService).migrateGeneralLedgerBalances(matchingPrisonerId, mockGeneralLedgerBalancesSyncRequest)
    }

    @Test
    fun `should call both internal migration and general ledger migration when feature flag is enabled and prisoner ID matches`() {
      val mockPrisonerBalancesSyncRequest = mock<PrisonerBalancesSyncRequest>()

      doNothing().whenever(internalMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)

      val result = dualMigrationService.migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)

      assertThat(result).isEqualTo(Unit)
      verify(internalMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)
      verify(generalLedgerMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)
    }

    @Test
    fun `should call migrateGeneralLedgerBalances for both internal migration and general ledger migration services when feature flag is enabled and prisoner ID matches`() {
      val mockGeneralLedgerBalancesSyncRequest = mock<GeneralLedgerBalancesSyncRequest>()

      doNothing().whenever(internalMigrationService).migrateGeneralLedgerBalances(matchingPrisonerId, mockGeneralLedgerBalancesSyncRequest)

      val result = dualMigrationService.migrateGeneralLedgerBalances(matchingPrisonerId, mockGeneralLedgerBalancesSyncRequest)

      assertThat(result).isEqualTo(Unit)
      verify(internalMigrationService).migrateGeneralLedgerBalances(matchingPrisonerId, mockGeneralLedgerBalancesSyncRequest)
      verify(generalLedgerMigrationService).migrateGeneralLedgerBalances(matchingPrisonerId, mockGeneralLedgerBalancesSyncRequest)
    }

    @Test
    fun `should log error when migratePrisonerBalances throws exception in general ledger migration`() {
      dualMigrationService = DualMigrationService(internalMigrationService, generalLedgerMigrationService, true, matchingPrisonerId)

      val mockPrisonerBalancesSyncRequest = mock<PrisonerBalancesSyncRequest>()

      whenever(generalLedgerMigrationService.migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest))
        .thenThrow(RuntimeException("API Down"))

      val result = dualMigrationService.migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)

      assertThat(result).isEqualTo(Unit)

      verify(internalMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)
      verify(generalLedgerMigrationService).migratePrisonerBalances(matchingPrisonerId, mockPrisonerBalancesSyncRequest)

      val logs = listAppender.list.map { it.formattedMessage }
      assertThat(logs).anyMatch {
        it.contains("Failed to migrate prisoner balances for prisoner $matchingPrisonerId to General Ledger")
      }
    }
  }
}
