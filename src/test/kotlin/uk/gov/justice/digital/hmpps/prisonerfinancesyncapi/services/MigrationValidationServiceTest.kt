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
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.MigrationBalanceValidationMismatchEvent
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

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

    fun createMockedNomisAccountBalances(prisonId: String, accountCode: Int, balance: BigDecimal) = PrisonerAccountPointInTimeBalance(
      prisonId = prisonId,
      accountCode = accountCode,
      balance = balance,
      holdBalance = BigDecimal.valueOf(0),
      transactionId = 3L,
      asOfTimestamp = LocalDateTime.now(),
    )

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
    fun `should return true if able to reconcile NOMIS balances with general ledger balances`() {
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

      assertThat(result).isTrue()

      verify(telemetryClient, times(0)).trackEvent(any(), any(), any())
    }

    @Test
    fun `should return false and send telemetry event if NOMIS balances do not match with general ledger balances`() {
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

      val aggregatedNomisBalances = BalanceAggregator.aggregateBalances(nomisBalances)

      val mismatchEvent = MigrationBalanceValidationMismatchEvent(
        prisonNumber = mockedPrisonNumber,
        nomisBalances = nomisBalances,
        generalLedgerBalances = generalLedgerBalances,
        aggregatedNomisBalances = aggregatedNomisBalances,
      )

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(generalLedgerBalances)

      val result = migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, nomisBalances)

      assertThat(result).isFalse()

      verify(telemetryClient).trackEvent(mismatchEvent.eventName, mismatchEvent.toStringMap(), null)
    }

    @Test
    fun `should return false and send telemetry event if sub account exists in NOMIS but not GL`() {
      val nomisBalances = listOf(
        createMockedNomisAccountBalances("LEI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("LEI", 2102, BigDecimal.valueOf(5.00)),
        createMockedNomisAccountBalances("LEI", 2103, BigDecimal.valueOf(10.00)),
      )

      val generalLedgerBalances = mutableMapOf(
        "CASH" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 250),
        "SPENDS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 500),
      )

      val aggregatedNomisBalances = BalanceAggregator.aggregateBalances(nomisBalances)

      val mismatchEvent = MigrationBalanceValidationMismatchEvent(
        prisonNumber = mockedPrisonNumber,
        nomisBalances = nomisBalances,
        generalLedgerBalances = generalLedgerBalances,
        aggregatedNomisBalances = aggregatedNomisBalances,
      )

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(generalLedgerBalances)

      val result = migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, nomisBalances)

      assertThat(result).isFalse()

      verify(telemetryClient).trackEvent(mismatchEvent.eventName, mismatchEvent.toStringMap(), null)
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

      assertThat(result).isTrue()

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

      assertThat(result).isTrue()

      verify(telemetryClient, times(0)).trackEvent(any(), any(), any())
    }

    @Test
    fun `should return false if general ledger has extra sub accounts with a non-zero balance`() {
      val nomisBalances = listOf(
        createMockedNomisAccountBalances("LEI", 2101, BigDecimal.valueOf(2.50)),
        createMockedNomisAccountBalances("LEI", 2102, BigDecimal.valueOf(5.00)),
      )

      val generalLedgerBalances = mutableMapOf(
        "CASH" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 250),
        "SPENDS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 500),
        "SAVINGS" to SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 100),

      )

      val aggregatedNomisBalances = BalanceAggregator.aggregateBalances(nomisBalances)

      val mismatchEvent = MigrationBalanceValidationMismatchEvent(
        prisonNumber = mockedPrisonNumber,
        nomisBalances = nomisBalances,
        generalLedgerBalances = generalLedgerBalances,
        aggregatedNomisBalances = aggregatedNomisBalances,
      )

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(generalLedgerBalances)

      val result = migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, nomisBalances)

      assertThat(result).isFalse()

      verify(telemetryClient).trackEvent(mismatchEvent.eventName, mismatchEvent.toStringMap(), null)
    }
  }
}
