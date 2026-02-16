package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal

class ToPenceExtensionTest {
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
    "1.15000000000,115",
    "100000.00,10000000",
    "1.99000000000,199",
  )
  fun `should cast Decimal to GL Long`(inputVal: BigDecimal, expected: Long) {
    assertThat(inputVal.toPence()).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource(
    "1.456",
    "1.000000000001",
    "99.999999999",
    "1.1455",
    "1.144",
    "1.99200000000",
  )
  fun `should throw ArithmeticException when casting Decimal to GL Long with more than 2 decimal places`(inputVal: BigDecimal) {
    assertThatThrownBy { inputVal.toPence() }.isInstanceOf(ArithmeticException::class.java)
  }
}
