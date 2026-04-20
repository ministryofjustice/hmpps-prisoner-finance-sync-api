package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class BalanceAggregatorTest {

  @Nested
  @DisplayName("aggregateNomisBalances")
  inner class AggregateNomisBalances {

    @Test
    fun `should return aggregate balance object for a single balance`() {
      val nomisBalances = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2101,
        balance = BigDecimal.valueOf(1.23),
        holdBalance = BigDecimal.valueOf(2.34),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val results = BalanceAggregator.aggregateBalances(listOf(nomisBalances))

      val account = results[2101]!!
      assertThat(results).hasSize(1)
      assertThat(account.accountCode).isEqualTo(2101)
      assertThat(account.balance).isEqualTo(BigDecimal.valueOf(1.23))
      assertThat(account.holdBalance).isEqualTo(BigDecimal.valueOf(2.34))
    }

    @Test
    fun `should return aggregate balances object for a multiple balances for the same prison Id`() {
      val nomisBalances1 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2101,
        balance = BigDecimal.valueOf(1.11),
        holdBalance = BigDecimal.valueOf(1.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances2 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2102,
        balance = BigDecimal.valueOf(2.22),
        holdBalance = BigDecimal.valueOf(2.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances3 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2103,
        balance = BigDecimal.valueOf(3.33),
        holdBalance = BigDecimal.valueOf(3.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val results = BalanceAggregator.aggregateBalances(
        listOf(nomisBalances1, nomisBalances2, nomisBalances3),
      )

      val cash = results[2101]!!
      val spends = results[2102]!!
      val savings = results[2103]!!

      assertThat(results).hasSize(3)
      assertThat(cash.accountCode).isEqualTo(2101)
      assertThat(cash.balance).isEqualTo(BigDecimal.valueOf(1.11))
      assertThat(cash.holdBalance).isEqualTo(BigDecimal.valueOf(1.50))

      assertThat(spends.accountCode).isEqualTo(2102)
      assertThat(spends.balance).isEqualTo(BigDecimal.valueOf(2.22))
      assertThat(spends.holdBalance).isEqualTo(BigDecimal.valueOf(2.50))

      assertThat(savings.accountCode).isEqualTo(2103)
      assertThat(savings.balance).isEqualTo(BigDecimal.valueOf(3.33))
      assertThat(savings.holdBalance).isEqualTo(BigDecimal.valueOf(3.50))
    }

    @Test
    fun `should return aggregate balances object for a multiple balances for the same account across multiple prisons`() {
      val nomisBalances1 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2101,
        balance = BigDecimal.valueOf(1.11),
        holdBalance = BigDecimal.valueOf(1.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances2 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2102,
        balance = BigDecimal.valueOf(2.22),
        holdBalance = BigDecimal.valueOf(2.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances3 = PrisonerAccountPointInTimeBalance(
        prisonId = "MDI",
        accountCode = 2101,
        balance = BigDecimal.valueOf(3.33),
        holdBalance = BigDecimal.valueOf(3.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances4 = PrisonerAccountPointInTimeBalance(
        prisonId = "MDI",
        accountCode = 2102,
        balance = BigDecimal.valueOf(4.44),
        holdBalance = BigDecimal.valueOf(4.50),
        transactionId = 3L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val results = BalanceAggregator.aggregateBalances(
        listOf(nomisBalances1, nomisBalances2, nomisBalances3, nomisBalances4),
      )

      val cash = results[2101]!!
      val spends = results[2102]!!

      assertThat(results).hasSize(2)
      assertThat(cash.accountCode).isEqualTo(2101)
      assertThat(cash.balance).isEqualTo(BigDecimal.valueOf(4.44))
      assertThat(cash.holdBalance).isEqualTo(BigDecimal.valueOf(5.00))

      assertThat(spends.accountCode).isEqualTo(2102)
      assertThat(spends.balance).isEqualTo(BigDecimal.valueOf(6.66))
      assertThat(spends.holdBalance).isEqualTo(BigDecimal.valueOf(7.00))
    }

    @Test
    fun `should return with the latest timestamp and transaction id for each sub account`() {
      val nomisBalances1 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2101,
        balance = BigDecimal.valueOf(1.11),
        holdBalance = BigDecimal.valueOf(1.50),
        transactionId = 1L,
        asOfTimestamp = LocalDateTime.now().minusDays(1),
      )

      val nomisBalances2 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2102,
        balance = BigDecimal.valueOf(2.22),
        holdBalance = BigDecimal.valueOf(2.50),
        transactionId = 1L,
        asOfTimestamp = LocalDateTime.now().minusDays(1),
      )

      val nomisBalances3 = PrisonerAccountPointInTimeBalance(
        prisonId = "MDI",
        accountCode = 2101,
        balance = BigDecimal.valueOf(3.33),
        holdBalance = BigDecimal.valueOf(3.50),
        transactionId = 2L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances4 = PrisonerAccountPointInTimeBalance(
        prisonId = "MDI",
        accountCode = 2102,
        balance = BigDecimal.valueOf(4.44),
        holdBalance = BigDecimal.valueOf(4.50),
        transactionId = 2L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val results = BalanceAggregator.aggregateBalances(
        listOf(nomisBalances1, nomisBalances2, nomisBalances3, nomisBalances4),
      )
      val cash = results[2101]!!
      val spends = results[2102]!!
      assertThat(results).hasSize(2)

      assertThat(cash.accountCode).isEqualTo(2101)
      assertThat(cash.asOfTimestamp).isEqualTo(nomisBalances3.asOfTimestamp)
      assertThat(cash.transactionId).isEqualTo(2L)

      assertThat(spends.accountCode).isEqualTo(2102)
      assertThat(spends.asOfTimestamp).isEqualTo(nomisBalances4.asOfTimestamp)
      assertThat(spends.transactionId).isEqualTo(2L)
    }
  }
}
