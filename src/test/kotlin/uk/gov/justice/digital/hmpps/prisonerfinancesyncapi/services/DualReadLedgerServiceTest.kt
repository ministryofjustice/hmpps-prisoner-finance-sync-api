package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService

@ExtendWith(MockitoExtension::class)
class DualReadLedgerServiceTest {

  @Mock
  private lateinit var generalLedger: GeneralLedgerService

  @Mock
  private lateinit var ledgerQueryService: LedgerQueryService

  private lateinit var listAppender: ListAppender<ILoggingEvent>

  private lateinit var dualReadLedgerService: DualReadLedgerService

  private val matchingPrisonerId = "A1234AA"

  private val logger = LoggerFactory.getLogger(DualReadLedgerService::class.java) as Logger

  @BeforeEach
  fun setup() {
    dualReadLedgerService = DualReadLedgerService(generalLedger, ledgerQueryService, true, matchingPrisonerId)
    listAppender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(listAppender)
  }

  @AfterEach
  fun tearDown() {
    logger.detachAppender(listAppender)
  }

  @Test
  fun `should log configuration on startup`() {
    dualReadLedgerService = DualReadLedgerService(generalLedger, ledgerQueryService, true, matchingPrisonerId)
    val logs = listAppender.list.map { it.formattedMessage }
    assertThat(logs).anyMatch {
      it.contains("General Ledger Dual Read Service initialized. Enabled: true. Test Prisoner ID: A1234AA")
    }
  }

  @Nested
  @DisplayName("prisonerReconciliation")
  inner class PrisonerReconciliation {
    val prisonNumber = "A1234AA"

    @Test
    fun `should call both internal ledger and GL when reconciling a prisoner`() {
      dualReadLedgerService.reconcilePrisoner(prisonNumber)

      verify(ledgerQueryService).listPrisonerBalancesByEstablishment(prisonNumber)
      verify(generalLedger).reconcilePrisoner(prisonNumber)
    }

    @Test
    fun `should handle exception when it's thrown by GL when reconciling a prisoner and log error`() {
      val expectedException = RuntimeException("Expected Exception")
      whenever(generalLedger.reconcilePrisoner(prisonNumber)).thenThrow(expectedException)

      val resultItem = listOf(mock<PrisonerEstablishmentBalanceDetails>())
      whenever(ledgerQueryService.listPrisonerBalancesByEstablishment(prisonNumber))
        .thenReturn(resultItem)

      val res = dualReadLedgerService.reconcilePrisoner(prisonNumber)

      verify(ledgerQueryService).listPrisonerBalancesByEstablishment(prisonNumber)
      verify(generalLedger).reconcilePrisoner(prisonNumber)

      val logs = listAppender.list.map { it.formattedMessage }
      assertThat(logs).anyMatch {
        it.contains("Failed to reconcile prisoner $prisonNumber to General Ledger")
      }
      assertThat(res).isEqualTo(PrisonerEstablishmentBalanceDetailsList(resultItem))
    }
  }
}
