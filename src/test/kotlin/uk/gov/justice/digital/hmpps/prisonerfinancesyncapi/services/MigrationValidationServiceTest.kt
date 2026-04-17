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
  lateinit var generalLedgerService: GeneralLedgerService

  @InjectMocks
  lateinit var migrationValidationService: MigrationValidationService

  @Nested
  @DisplayName("aggregateNomisBalances")
  inner class AggregateNomisBalances {

    @Test
    fun `should return aggregate balance object for a single balance`() {
      val nomisBalances = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2201,
        balance = BigDecimal.valueOf(1.23),
        holdBalance = BigDecimal.valueOf(2.34),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val results = migrationValidationService.aggregateBalances(listOf(nomisBalances))

      assertThat(results).hasSize(1)
      assertThat(results[0].accountCode).isEqualTo(2201)
      assertThat(results[0].balance).isEqualTo(123)
      assertThat(results[0].holdBalance).isEqualTo(234)
    }

    @Test
    fun `should return aggregate balances object for a multiple balances for the same prison Id`() {
      val nomisBalances1 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2201,
        balance = BigDecimal.valueOf(1.11),
        holdBalance = BigDecimal.valueOf(1.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances2 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2202,
        balance = BigDecimal.valueOf(2.22),
        holdBalance = BigDecimal.valueOf(2.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances3 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2203,
        balance = BigDecimal.valueOf(3.33),
        holdBalance = BigDecimal.valueOf(3.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val results = migrationValidationService.aggregateBalances(
        listOf(nomisBalances1, nomisBalances2, nomisBalances3),
      )

      assertThat(results).hasSize(3)
      assertThat(results[0].accountCode).isEqualTo(2201)
      assertThat(results[0].balance).isEqualTo(111)
      assertThat(results[0].holdBalance).isEqualTo(150)

      assertThat(results[1].accountCode).isEqualTo(2202)
      assertThat(results[1].balance).isEqualTo(222)
      assertThat(results[1].holdBalance).isEqualTo(250)

      assertThat(results[2].accountCode).isEqualTo(2203)
      assertThat(results[2].balance).isEqualTo(333)
      assertThat(results[2].holdBalance).isEqualTo(350)
    }

    @Test
    fun `should return aggregate balances object for a multiple balances for the same account across multiple prisons`() {
      val nomisBalances1 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2201,
        balance = BigDecimal.valueOf(1.11),
        holdBalance = BigDecimal.valueOf(1.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances2 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2202,
        balance = BigDecimal.valueOf(2.22),
        holdBalance = BigDecimal.valueOf(2.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances3 = PrisonerAccountPointInTimeBalance(
        prisonId = "MDI",
        accountCode = 2201,
        balance = BigDecimal.valueOf(3.33),
        holdBalance = BigDecimal.valueOf(3.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances4 = PrisonerAccountPointInTimeBalance(
        prisonId = "MDI",
        accountCode = 2202,
        balance = BigDecimal.valueOf(4.44),
        holdBalance = BigDecimal.valueOf(4.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val results = migrationValidationService.aggregateBalances(
        listOf(nomisBalances1, nomisBalances2, nomisBalances3, nomisBalances4),
      )

      assertThat(results).hasSize(2)
      assertThat(results[0].accountCode).isEqualTo(2201)
      assertThat(results[0].balance).isEqualTo(444)
      assertThat(results[0].holdBalance).isEqualTo(500)

      assertThat(results[1].accountCode).isEqualTo(2202)
      assertThat(results[1].balance).isEqualTo(666)
      assertThat(results[1].holdBalance).isEqualTo(700)
    }
  }

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
