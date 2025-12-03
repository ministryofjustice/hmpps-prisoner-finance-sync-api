package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PrisonAccountDetailsListTest {
  @Test
  fun `getItems should return the list of prison account details`() {
    val account1 = PrisonAccountDetails(
      code = 1000,
      name = "Cash",
      prisonId = "MDI",
      classification = "CR",
      postingType = "1234",
      balance = BigDecimal(1234),
    )
    val account2 = PrisonAccountDetails(
      code = 1000,
      name = "Bank Account",
      prisonId = "MDI",
      classification = "CR",
      postingType = "100",
      balance = BigDecimal(100),
    )

    val list = PrisonAccountDetailsList(items = listOf(account1, account2))

    val result = list.items

    assertThat(result).containsExactly(account1, account2)
  }
}
