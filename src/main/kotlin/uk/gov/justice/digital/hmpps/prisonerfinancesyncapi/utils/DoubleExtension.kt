package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils

import java.math.BigDecimal

fun Double.toPence(): Long = BigDecimal.valueOf(this).movePointRight(2).toLong()
