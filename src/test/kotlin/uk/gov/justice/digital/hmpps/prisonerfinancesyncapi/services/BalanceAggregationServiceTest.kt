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
class BalanceAggregationServiceTest {

  val balanceAggregationService = BalanceAggregationService()

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

      val results = balanceAggregationService.aggregateBalances(listOf(nomisBalances))

      assertThat(results).hasSize(1)
      assertThat(results[0].accountCode).isEqualTo(2201)
      assertThat(results[0].balance).isEqualTo(BigDecimal.valueOf(1.23))
      assertThat(results[0].holdBalance).isEqualTo(BigDecimal.valueOf(2.34))
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

      val results = balanceAggregationService.aggregateBalances(
        listOf(nomisBalances1, nomisBalances2, nomisBalances3),
      )

      assertThat(results).hasSize(3)
      assertThat(results[0].accountCode).isEqualTo(2201)
      assertThat(results[0].balance).isEqualTo(BigDecimal.valueOf(1.11))
      assertThat(results[0].holdBalance).isEqualTo(BigDecimal.valueOf(1.50))

      assertThat(results[1].accountCode).isEqualTo(2202)
      assertThat(results[1].balance).isEqualTo(BigDecimal.valueOf(2.22))
      assertThat(results[1].holdBalance).isEqualTo(BigDecimal.valueOf(2.50))

      assertThat(results[2].accountCode).isEqualTo(2203)
      assertThat(results[2].balance).isEqualTo(BigDecimal.valueOf(3.33))
      assertThat(results[2].holdBalance).isEqualTo(BigDecimal.valueOf(3.50))
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

      val results = balanceAggregationService.aggregateBalances(
        listOf(nomisBalances1, nomisBalances2, nomisBalances3, nomisBalances4),
      )

      assertThat(results).hasSize(2)
      assertThat(results[0].accountCode).isEqualTo(2201)
      assertThat(results[0].balance).isEqualTo(BigDecimal.valueOf(4.44))
      assertThat(results[0].holdBalance).isEqualTo(BigDecimal.valueOf(5.00))

      assertThat(results[1].accountCode).isEqualTo(2202)
      assertThat(results[1].balance).isEqualTo(BigDecimal.valueOf(6.66))
      assertThat(results[1].holdBalance).isEqualTo(BigDecimal.valueOf(7.00))
    }

    @Test
    fun `should return with the latest timestamp and transaction id for each sub account`() {
      val nomisBalances1 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2201,
        balance = BigDecimal.valueOf(1.11),
        holdBalance = BigDecimal.valueOf(1.50),
        transactionId = 1L,
        asOfTimestamp = LocalDateTime.now().minusDays(1),
      )

      val nomisBalances2 = PrisonerAccountPointInTimeBalance(
        prisonId = "LEI",
        accountCode = 2202,
        balance = BigDecimal.valueOf(2.22),
        holdBalance = BigDecimal.valueOf(2.50),
        transactionId = 1L,
        asOfTimestamp = LocalDateTime.now().minusDays(1),
      )

      val nomisBalances3 = PrisonerAccountPointInTimeBalance(
        prisonId = "MDI",
        accountCode = 2201,
        balance = BigDecimal.valueOf(3.33),
        holdBalance = BigDecimal.valueOf(3.50),
        transactionId = 2L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val nomisBalances4 = PrisonerAccountPointInTimeBalance(
        prisonId = "MDI",
        accountCode = 2202,
        balance = BigDecimal.valueOf(4.44),
        holdBalance = BigDecimal.valueOf(4.50),
        transactionId = 2L,
        asOfTimestamp = LocalDateTime.now(),
      )

      val results = balanceAggregationService.aggregateBalances(
        listOf(nomisBalances1, nomisBalances2, nomisBalances3, nomisBalances4),
      )

      assertThat(results).hasSize(2)
      assertThat(results[0].accountCode).isEqualTo(2201)
      assertThat(results[0].asOfTimestamp).isEqualTo(nomisBalances3.asOfTimestamp)
      assertThat(results[0].transactionId).isEqualTo(2L)

      assertThat(results[1].accountCode).isEqualTo(2202)
      assertThat(results[1].asOfTimestamp).isEqualTo(nomisBalances4.asOfTimestamp)
      assertThat(results[1].transactionId).isEqualTo(2L)
    }
  }
}
