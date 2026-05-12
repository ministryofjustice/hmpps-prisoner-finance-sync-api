package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.DiscrepancyProperties
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GeneralLedgerDiscrepancyDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetails
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.abs

@ExtendWith(MockitoExtension::class)
class MigrationValidationServiceTest {

  @Mock
  private lateinit var generalLedgerService: GeneralLedgerService

  @Mock
  private lateinit var telemetryClient: TelemetryClient

  @InjectMocks
  lateinit var migrationValidationService: MigrationValidationService

  @Nested
  @DisplayName("validatePrisonerBalances")
  inner class ValidatePrisonerBalances {

    val mockedPrisonNumber = "A1234BC"
    val expectedErrorName = "prisoner-finance-sync-api-balance-validation-mismatch"

    fun createMockedNomisAccountBalances(prisonId: String, accountCode: Int, balance: BigDecimal) = PrisonerAccountPointInTimeBalance(
      prisonId = prisonId,
      accountCode = accountCode,
      balance = balance,
      holdBalance = BigDecimal.valueOf(0),
      transactionId = 3L,
      asOfTimestamp = LocalDateTime.now(),
    )

    fun createPropertiesFromDiscrepancyDetails(descrepancyDetials: GeneralLedgerDiscrepancyDetails): DiscrepancyProperties {
      val discrepancyProperties = DiscrepancyProperties(
        message = descrepancyDetials.message,
        prisonerId = descrepancyDetials.prisonerId,
        accountType = descrepancyDetials.accountType,
        glBreakdown = descrepancyDetials.glBreakdown,
        legacyBreakdown = descrepancyDetials.legacyBreakdown,
      )

      return discrepancyProperties
    }

    fun createMetricsFromDiscrepancyDetails(descrepancyDetials: GeneralLedgerDiscrepancyDetails): Map<String, Double> {
      val discrepancyMetrics = mapOf(
        "generalLedgerBalance" to descrepancyDetials.generalLedgerBalance.toDouble(),
        "legacyBalance" to descrepancyDetials.legacyAggregatedBalance.toDouble(),
        "discrepancy" to descrepancyDetials.discrepancy.toDouble(),
        "absoluteDiscrepancy" to abs(n = descrepancyDetials.discrepancy).toDouble(),
      )

      return discrepancyMetrics
    }

    @Test
    fun `should call general ledger for requested prisoners balances`() {
      val mockedGLPrisonerBalances = mutableMapOf(
        "CASH" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 500),
        "SPENDS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 1000),
        "SAVINGS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 2000),
      )

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(mockedGLPrisonerBalances)

      migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, emptyList())

      verify(generalLedgerService).getGLPrisonerBalances(mockedPrisonNumber)
    }

    @Test
    fun `should return an empty array and send no events if able to reconcile NOMIS balances with general ledger balances`() {
      val mockedGLPrisonerBalances = mutableMapOf(
        "CASH" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 500),
        "SPENDS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 1000),
        "SAVINGS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 2000),
      )

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(mockedGLPrisonerBalances)

      val mockedNomisAccountBalances = listOf(
        createMockedNomisAccountBalances("LEI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("LEI", 2102, BigDecimal.valueOf(5.00)),
        createMockedNomisAccountBalances("LEI", 2103, BigDecimal.valueOf(10.00)),
        createMockedNomisAccountBalances("MDI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("MDI", 2102, BigDecimal.valueOf(5.00)),
        createMockedNomisAccountBalances("MDI", 2103, BigDecimal.valueOf(10.00)),
      )

      val result = migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, mockedNomisAccountBalances)

      assertThat(result.size == 0).isTrue()

      verify(telemetryClient, times(0)).trackEvent(any(), any(), any())
    }

    @Test
    fun `should return true if general ledger has extra sub accounts with 0 balance - for example template accounts`() {
      val nomisBalances = listOf(
        createMockedNomisAccountBalances("LEI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("LEI", 2102, BigDecimal.valueOf(5.00)),
      )

      val generalLedgerBalances = mutableMapOf(
        "CASH" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 250),
        "SPENDS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 500),
        "SAVINGS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 0),
      )

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(generalLedgerBalances)

      val result = migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, nomisBalances)

      assertThat(result.size == 0).isTrue()

      verify(telemetryClient, times(0)).trackEvent(any(), any(), any())
    }

    @Test
    fun `should return true if NOMIS sends an account balance of 0 and there is no sub account for that in GL yet - (Sub Accounts are created on transaction not when accounts are migrated)`() {
      val nomisBalances = listOf(
        createMockedNomisAccountBalances("LEI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("LEI", 2102, BigDecimal.valueOf(5.00)),
        createMockedNomisAccountBalances("LEI", 2103, BigDecimal.valueOf(0.00)),
      )

      val generalLedgerBalances = mutableMapOf(
        "CASH" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 250),
        "SPENDS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 500),
      )

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(generalLedgerBalances)

      val result = migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, nomisBalances)

      assertThat(result.size == 0).isTrue()

      verify(telemetryClient, times(0)).trackEvent(any(), any(), any())
    }

    @Test
    fun `should return a list of one discrepancy and send telemetry event if general ledger has extra sub accounts with a non-zero balance`() {
      val nomisBalances = listOf(
        createMockedNomisAccountBalances("LEI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("LEI", 2102, BigDecimal.valueOf(5.00)),
      )

      val generalLedgerBalances = mutableMapOf(
        "CASH" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 250),
        "SPENDS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 500),
        "SAVINGS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 100),
      )

      val discrepancyDetail = GeneralLedgerDiscrepancyDetails(
        message = "NOMIS balances do not match with general ledger balances",
        prisonerId = mockedPrisonNumber,
        accountType = "SAVINGS",
        legacyAggregatedBalance = 0,
        generalLedgerBalance = 100,
        discrepancy = 100,
        glBreakdown = listOf(generalLedgerBalances.getValue("SAVINGS")),
        legacyBreakdown = emptyList(),
      )

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(generalLedgerBalances)

      val result = migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, nomisBalances)

      assertThat(result.size == 1).isTrue()
      assertThat(result[0]).isEqualTo(discrepancyDetail)

      val discrepancyProperties = createPropertiesFromDiscrepancyDetails(descrepancyDetials = discrepancyDetail)
      val discrepancyMetrics = createMetricsFromDiscrepancyDetails(descrepancyDetials = discrepancyDetail)

      verify(telemetryClient).trackEvent(expectedErrorName, discrepancyProperties.toStringMap(), discrepancyMetrics)
    }

    @Test
    fun `should return list of one discrepancy and send telemetry event if sub account exists in NOMIS but not GL`() {
      val nomisBalances = listOf(
        createMockedNomisAccountBalances("LEI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("LEI", 2102, BigDecimal.valueOf(5.00)),
        createMockedNomisAccountBalances("LEI", 2103, BigDecimal.valueOf(10.00)),
      )

      val generalLedgerBalances = mutableMapOf(
        "CASH" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 250),
        "SPENDS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 500),
      )

      val discrepancyDetail = GeneralLedgerDiscrepancyDetails(
        message = "NOMIS balances do not match with general ledger balances",
        prisonerId = mockedPrisonNumber,
        accountType = "SAVINGS",
        legacyAggregatedBalance = 1000,
        generalLedgerBalance = 0,
        discrepancy = 1000,
        glBreakdown = emptyList(),
        legacyBreakdown = listOf(
          PrisonerEstablishmentBalanceDetails(
            prisonId = "LEI",
            accountCode = 2103,
            totalBalance = BigDecimal.valueOf(10.00),
            holdBalance = BigDecimal.valueOf(0),
          ),
        ),
      )

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(generalLedgerBalances)

      val result = migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, nomisBalances)

      assertThat(result.size == 1).isTrue()
      assertThat(result[0]).isEqualTo(discrepancyDetail)

      val discrepancyProperties = createPropertiesFromDiscrepancyDetails(descrepancyDetials = discrepancyDetail)
      val discrepancyMetrics = createMetricsFromDiscrepancyDetails(descrepancyDetials = discrepancyDetail)

      verify(telemetryClient).trackEvent(expectedErrorName, discrepancyProperties.toStringMap(), discrepancyMetrics)
    }

    @Test
    fun `should return list of one discrepancy and send telemetry event if NOMIS balances do not match with general ledger balances`() {
      val nomisBalances = listOf(
        createMockedNomisAccountBalances("LEI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("LEI", 2102, BigDecimal.valueOf(5.00)),
        createMockedNomisAccountBalances("LEI", 2103, BigDecimal.valueOf(10.00)),
        createMockedNomisAccountBalances("MDI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("MDI", 2102, BigDecimal.valueOf(5.00)),
        createMockedNomisAccountBalances("MDI", 2103, BigDecimal.valueOf(10.00)),
      )

      val generalLedgerBalances = mutableMapOf(
        "CASH" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 999),
        "SPENDS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 1000),
        "SAVINGS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 2000),
      )

      val discrepancyDetail = GeneralLedgerDiscrepancyDetails(
        message = "NOMIS balances do not match with general ledger balances",
        prisonerId = mockedPrisonNumber,
        accountType = "CASH",
        legacyAggregatedBalance = 500,
        generalLedgerBalance = 999,
        discrepancy = 999 - 500,
        glBreakdown = listOf(generalLedgerBalances["CASH"]!!),
        legacyBreakdown = listOf(
          PrisonerEstablishmentBalanceDetails(
            prisonId = "LEI",
            accountCode = 2101,
            totalBalance = BigDecimal.valueOf(2.50),
            holdBalance = BigDecimal.valueOf(0),
          ),
          PrisonerEstablishmentBalanceDetails(
            prisonId = "MDI",
            accountCode = 2101,
            totalBalance = BigDecimal.valueOf(2.50),
            holdBalance = BigDecimal.valueOf(0),
          ),
        ),
      )

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(generalLedgerBalances)

      val result = migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, nomisBalances)

      assertThat(result.size == 1).isTrue()
      assertThat(result[0]).isEqualTo(discrepancyDetail)

      val discrepancyProperties = createPropertiesFromDiscrepancyDetails(descrepancyDetials = discrepancyDetail)
      val discrepancyMetrics = createMetricsFromDiscrepancyDetails(descrepancyDetials = discrepancyDetail)

      verify(telemetryClient).trackEvent(expectedErrorName, discrepancyProperties.toStringMap(), discrepancyMetrics)
    }

    @Test
    fun `should return list of multiple discrepancies and send multiple telemetry events if multiple NOMIS balances do not match with general ledger balances`() {
      val nomisBalances = listOf(
        createMockedNomisAccountBalances("LEI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("LEI", 2102, BigDecimal.valueOf(5.00)),
        createMockedNomisAccountBalances("LEI", 2103, BigDecimal.valueOf(10.00)),
        createMockedNomisAccountBalances("MDI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("MDI", 2102, BigDecimal.valueOf(5.00)),
        createMockedNomisAccountBalances("MDI", 2103, BigDecimal.valueOf(10.00)),
      )

      val generalLedgerBalances = mutableMapOf(
        "CASH" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 50000),
        "SPENDS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 50000),
        "SAVINGS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 50000),
      )

      val cashDiscrepancyDetails = GeneralLedgerDiscrepancyDetails(
        message = "NOMIS balances do not match with general ledger balances",
        prisonerId = mockedPrisonNumber,
        accountType = "CASH",
        legacyAggregatedBalance = 500,
        generalLedgerBalance = 50000,
        discrepancy = abs(500 - 50000).toLong(),
        glBreakdown = listOf(generalLedgerBalances["CASH"]!!),
        legacyBreakdown = listOf(
          PrisonerEstablishmentBalanceDetails(
            prisonId = "LEI",
            accountCode = 2101,
            totalBalance = BigDecimal.valueOf(2.50),
            holdBalance = BigDecimal.valueOf(0),
          ),
          PrisonerEstablishmentBalanceDetails(
            prisonId = "MDI",
            accountCode = 2101,
            totalBalance = BigDecimal.valueOf(2.50),
            holdBalance = BigDecimal.valueOf(0),
          ),
        ),
      )

      val spendsDiscrepancyDetails = GeneralLedgerDiscrepancyDetails(
        message = "NOMIS balances do not match with general ledger balances",
        prisonerId = mockedPrisonNumber,
        accountType = "SPENDS",
        legacyAggregatedBalance = 1000,
        generalLedgerBalance = 50000,
        discrepancy = abs(1000 - 50000).toLong(),
        glBreakdown = listOf(generalLedgerBalances["SPENDS"]!!),
        legacyBreakdown = listOf(
          PrisonerEstablishmentBalanceDetails(
            prisonId = "LEI",
            accountCode = 2102,
            totalBalance = BigDecimal.valueOf(5.00),
            holdBalance = BigDecimal.valueOf(0),
          ),
          PrisonerEstablishmentBalanceDetails(
            prisonId = "MDI",
            accountCode = 2102,
            totalBalance = BigDecimal.valueOf(5.00),
            holdBalance = BigDecimal.valueOf(0),
          ),
        ),
      )

      val savingsDiscrepancyDetails = GeneralLedgerDiscrepancyDetails(
        message = "NOMIS balances do not match with general ledger balances",
        prisonerId = mockedPrisonNumber,
        accountType = "SAVINGS",
        legacyAggregatedBalance = 2000,
        generalLedgerBalance = 50000,
        discrepancy = abs(2000 - 50000).toLong(),
        glBreakdown = listOf(generalLedgerBalances["SAVINGS"]!!),
        legacyBreakdown = listOf(
          PrisonerEstablishmentBalanceDetails(
            prisonId = "LEI",
            accountCode = 2103,
            totalBalance = BigDecimal.valueOf(10.00),
            holdBalance = BigDecimal.valueOf(0),
          ),
          PrisonerEstablishmentBalanceDetails(
            prisonId = "MDI",
            accountCode = 2103,
            totalBalance = BigDecimal.valueOf(10.00),
            holdBalance = BigDecimal.valueOf(0),
          ),
        ),
      )

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(generalLedgerBalances)

      val result = migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, nomisBalances)

      assertThat(result.size == 3).isTrue()
      assertThat(result).containsExactlyInAnyOrder(cashDiscrepancyDetails, spendsDiscrepancyDetails, savingsDiscrepancyDetails)

      val cashDiscrepancyProperties = createPropertiesFromDiscrepancyDetails(descrepancyDetials = cashDiscrepancyDetails)
      val cashDiscrepancyMetrics = createMetricsFromDiscrepancyDetails(descrepancyDetials = cashDiscrepancyDetails)

      verify(telemetryClient).trackEvent(expectedErrorName, cashDiscrepancyProperties.toStringMap(), cashDiscrepancyMetrics)

      val spendsDiscrepancyProperties = createPropertiesFromDiscrepancyDetails(descrepancyDetials = spendsDiscrepancyDetails)
      val spendsDiscrepancyMetrics = createMetricsFromDiscrepancyDetails(descrepancyDetials = spendsDiscrepancyDetails)

      verify(telemetryClient).trackEvent(expectedErrorName, spendsDiscrepancyProperties.toStringMap(), spendsDiscrepancyMetrics)

      val savingsDiscrepancyProperties = createPropertiesFromDiscrepancyDetails(descrepancyDetials = savingsDiscrepancyDetails)
      val savingsDiscrepancyMetrics = createMetricsFromDiscrepancyDetails(descrepancyDetials = savingsDiscrepancyDetails)

      verify(telemetryClient).trackEvent(expectedErrorName, savingsDiscrepancyProperties.toStringMap(), savingsDiscrepancyMetrics)
    }
  }
}
