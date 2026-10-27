package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

fun LocalDateTime.toUtcInstant(): Instant = atZone(ZoneId.of("Europe/London")).toInstant()
