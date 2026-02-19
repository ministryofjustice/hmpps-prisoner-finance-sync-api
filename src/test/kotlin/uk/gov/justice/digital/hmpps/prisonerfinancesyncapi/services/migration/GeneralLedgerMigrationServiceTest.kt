package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateStatementBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.StatementBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerAccountResolver
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.LedgerAccountMappingService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@DisplayName("General Ledger Migration Service Test")
class GeneralLedgerMigrationServiceTest {

  @Mock
  private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @Mock
  private lateinit var accountResolver: GeneralLedgerAccountResolver

  @Spy
  private val timeConversionService = TimeConversionService()

  @Mock
  private lateinit var telemetryClient: TelemetryClient

  @InjectMocks
  private lateinit var generalLedgerMigrationService: GeneralLedgerMigrationService

  private val accountMapping = LedgerAccountMappingService()

  private lateinit var listAppender: ListAppender<ILoggingEvent>
  private val logger = LoggerFactory.getLogger(GeneralLedgerMigrationService::class.java) as Logger

  @BeforeEach
  fun setup() {
    listAppender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(listAppender)
  }

  val prisonNumber = "A1234AA"

  @Test
  fun `should aggregate multiple balances of different account codes from different establishments when called`() {
    val req = PrisonerBalancesSyncRequest(
      listOf(
        PrisonerAccountPointInTimeBalance(
          "TES",
          2101,
          BigDecimal("10"),
          BigDecimal.ZERO,
          1,
          LocalDateTime.now() - Duration.ofDays(1),
        ),
        PrisonerAccountPointInTimeBalance(
          "MDI",
          2101,
          BigDecimal("20"),
          BigDecimal.ZERO,
          1,
          LocalDateTime.now(),
        ),
        PrisonerAccountPointInTimeBalance(
          "MDI",
          2102,
          BigDecimal("33"),
          BigDecimal.ZERO,
          1,
          LocalDateTime.now() - Duration.ofDays(3),
        ),
      ),
    )

    val subAccounts = mutableMapOf<Int, UUID>()

    for (balance in req.accountBalances) {
      subAccounts[balance.accountCode] = UUID.randomUUID()
    }
    for ((code, subAccountId) in subAccounts) {
      whenever(
        accountResolver.resolveSubAccount(
          eq(""),
          eq(prisonNumber),
          eq(code),
          eq(""),
          any(),
        ),
      ).thenReturn(
        subAccountId,
      )
    }

    whenever(generalLedgerApiClient.migrateSubAccountBalance(any(), any())).thenReturn(
      StatementBalanceResponse(
        123,
        UUID.randomUUID(),
        Instant.now(),
      ),
    )

    generalLedgerMigrationService.migratePrisonerBalances(prisonNumber, req)

    for ((code, subAccountId) in subAccounts) {
      val request = CreateStatementBalanceRequest(
        req.accountBalances
          .filter { it.accountCode == code }
          .sumOf { it.balance }.toPence(),
        req.accountBalances
          .filter { it.accountCode == code }
          .maxOf { timeConversionService.toUtcInstant(it.asOfTimestamp) },
      )
      verify(generalLedgerApiClient).migrateSubAccountBalance(subAccountId, request)
      val logs = listAppender.list.map { it.formattedMessage }
      assertTrue(logs[logs.size - 1].contains("Successfully migrated balance "))
    }
    verify(telemetryClient, times(2)).trackEvent(eq(generalLedgerMigrationService.telemetryMigrationEvent), any(), eq(null))
  }
}
