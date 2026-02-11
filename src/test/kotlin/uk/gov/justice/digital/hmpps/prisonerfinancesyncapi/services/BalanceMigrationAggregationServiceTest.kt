package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration.BalanceMigrationAggregationService

@ExtendWith(MockitoExtension::class)
@DisplayName("Balance Migration Aggregation Test")
class BalanceMigrationAggregationServiceTest {

  private var balanceMigrationAggregationService = BalanceMigrationAggregationService()

  @ParameterizedTest
  @CsvSource("2101", "2102", "2103")
  fun `Should return balance of 0 when aggregating an empty prisonerBalanceRequest for all account codes`(accountCode: Int) {
    val request = PrisonerBalancesSyncRequest(
      accountBalances = emptyList(),
    )

    val balance = balanceMigrationAggregationService.aggregate(request, accountCode)

    assertThat(balance).isEqualTo(0)
  }
}
