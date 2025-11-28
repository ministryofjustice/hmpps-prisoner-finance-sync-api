package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeConversionServiceTest {

  private lateinit var timeConversionService: TimeConversionService

  @BeforeEach
  fun setUp() {
    timeConversionService = TimeConversionService()
  }

  @Nested
  @DisplayName("toUtcInstant conversion tests (LocalDateTime)")
  inner class ToUtcInstantTests {

    @Test
    fun `should correctly convert a standard time (GMT) to UTC`() {
      val winterDateTimeLondon = LocalDateTime.of(2024, 1, 15, 10, 30, 0)
      val expectedUtcInstant = ZonedDateTime.of(winterDateTimeLondon, ZoneId.of("UTC")).toInstant()

      val actualUtcInstant = timeConversionService.toUtcInstant(winterDateTimeLondon)

      assertThat(actualUtcInstant).isEqualTo(expectedUtcInstant)
      assertThat(actualUtcInstant.toString()).isEqualTo("2024-01-15T10:30:00Z")
    }

    @Test
    fun `should correctly convert a BST time (summer) to UTC`() {
      val summerDateTimeLondon = LocalDateTime.of(2024, 7, 10, 15, 45, 0)
      val expectedUtcDateTime = summerDateTimeLondon.minusHours(1)
      val expectedUtcInstant = ZonedDateTime.of(expectedUtcDateTime, ZoneId.of("UTC")).toInstant()

      val actualUtcInstant = timeConversionService.toUtcInstant(summerDateTimeLondon)

      assertThat(actualUtcInstant).isEqualTo(expectedUtcInstant)
      assertThat(actualUtcInstant.toString()).isEqualTo("2024-07-10T14:45:00Z")
    }

    @Test
    fun `should correctly handle the moment daylight saving begins (Spring Forward)`() {
      val dstStartDateTimeLondon = LocalDateTime.of(2024, 3, 31, 2, 30, 0)
      val expectedUtcInstant = ZonedDateTime.of(2024, 3, 31, 1, 30, 0, 0, ZoneId.of("UTC")).toInstant()

      val actualUtcInstant = timeConversionService.toUtcInstant(dstStartDateTimeLondon)

      assertThat(actualUtcInstant).isEqualTo(expectedUtcInstant)
    }
  }

  @Nested
  @DisplayName("toUtcStartOfDay conversion tests (LocalDate)")
  inner class ToUtcStartOfDayTests {

    @Test
    fun `should return midnight UTC for a date in GMT (winter)`() {
      val winterDateLondon = LocalDate.of(2024, 1, 15)
      val expectedInstant = Instant.parse("2024-01-15T00:00:00Z")

      val actualUtcInstant = timeConversionService.toUtcStartOfDay(winterDateLondon)

      assertThat(actualUtcInstant).isEqualTo(expectedInstant)
    }

    @Test
    fun `toUtcStartOfDay should return 2300 the previous day UTC for a date in BST (summer)`() {
      val summerDateLondon = LocalDate.of(2024, 7, 10)
      val expectedInstant = Instant.parse("2024-07-09T23:00:00Z")

      val actualUtcInstant = timeConversionService.toUtcStartOfDay(summerDateLondon)

      assertThat(actualUtcInstant).isEqualTo(expectedInstant)
    }

    @Test
    fun `toUtcStartOfDay should correctly handle a LocalDate spanning the autumn DST change (Fall Back)`() {
      val dstEndDayLondon = LocalDate.of(2024, 10, 27)
      val expectedInstant = Instant.parse("2024-10-26T23:00:00Z")

      val actualUtcInstant = timeConversionService.toUtcStartOfDay(dstEndDayLondon)

      assertThat(actualUtcInstant).isEqualTo(expectedInstant)
    }
  }
}
