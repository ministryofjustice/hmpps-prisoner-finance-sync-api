package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.services

import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class TimeConversionService {

  private val legacyZoneId = ZoneId.of("Europe/London")

  /**
   * Converts a local date-time from the legacy system's timezone
   * to a universally consistent UTC Instant.
   */
  fun toUtcInstant(localDateTime: LocalDateTime): Instant = localDateTime.atZone(legacyZoneId).toInstant()

  /**
   * Converts a LocalDate from the legacy system's timezone
   * to an Instant at the start of that day in UTC.
   */
  fun toUtcStartOfDay(localDate: LocalDate): Instant = localDate.atStartOfDay(legacyZoneId).toInstant()
}
