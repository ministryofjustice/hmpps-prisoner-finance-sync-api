package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.utils

import org.springframework.test.web.reactive.server.JsonPathAssertions
import org.springframework.test.web.reactive.server.WebTestClient
import java.math.BigDecimal

fun assertFinancialEquality(actual: BigDecimal, expected: BigDecimal, description: String? = null) {
  if (actual.setScale(2) != expected.setScale(2)) {
    throw AssertionError("Value mismatch! Expected: $expected, Actual: $actual")
  }
}

fun JsonPathAssertions.isMoneyEqual(expected: BigDecimal): WebTestClient.BodyContentSpec = this.value<Any> { actual ->
  // emulating the default isEqualTo behaviour for Lists
  val result = if (actual is List<*>) {
    require(actual.isNotEmpty()) { "JSON path returned an empty list" }
    require(actual.size == 1) { "JSON path returned multiple values: $actual" }
    actual[0]
  } else {
    actual
  }

  val actualBigDecimal = toStrictBigDecimal(result)
  assertFinancialEquality(actualBigDecimal, expected)
}

fun toStrictBigDecimal(value: Any?) = when (value) {
  is BigDecimal -> value
  is Long -> BigDecimal(value.toString())
  is Int -> BigDecimal(value.toString())
  is String -> BigDecimal(value)
  else -> throw AssertionError("Expected a type compatible with BigDecimal but got ${value?.javaClass?.name}")
}

fun JsonPathAssertions.isSumMoneyEqual(expected: BigDecimal): WebTestClient.BodyContentSpec = this.value<List<Any>> { actualList ->
  val total = actualList.fold(BigDecimal.ZERO) { acc, item ->
    acc.add(toStrictBigDecimal(item))
  }
  assertFinancialEquality(total, expected, "Total Sum")
}
