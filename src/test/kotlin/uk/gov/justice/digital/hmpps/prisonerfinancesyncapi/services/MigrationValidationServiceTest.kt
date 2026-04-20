package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MigrationValidationServiceTest {

  @Mock
  private lateinit var generalLedgerService: GeneralLedgerService

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
    fun `should return true if able to reconcile account balances with general ledger balances`() {
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
    }

    @Test
    fun `should return false if unable to reconcile account balances with general ledger balances`() {
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

      whenever(generalLedgerService.getGLPrisonerBalances(mockedPrisonNumber)).thenReturn(generalLedgerBalances)

      val result = migrationValidationService.validatePrisonerBalances(mockedPrisonNumber, nomisBalances)

      assertThat(result).isFalse()
    }
  }
}
