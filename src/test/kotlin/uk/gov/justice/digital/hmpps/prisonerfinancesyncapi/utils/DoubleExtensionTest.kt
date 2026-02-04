package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DoubleExtensionTest {
  @ParameterizedTest
  @CsvSource(
    "1.0,100",
    "9.99,999",
    "1.10,110",
    "1.99,199",
    "1.05,105",
    "0.09,9",
    "1.5,150",
    "3.33,333",
    "9.99,999",
    "0.01,1",
    "1.15,115",
    "100000.00,10000000",
  )
  fun `should cast Double to GL Long`(inputVal: Double, expected: Long) {
    assertThat(inputVal.toPence()).isEqualTo(expected)
  }
}
