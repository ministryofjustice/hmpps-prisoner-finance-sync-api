package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils

import java.math.BigDecimal

fun BigDecimal.toPence(): Long = movePointRight(2).toLong()

fun Double.toPence(): Long = BigDecimal.valueOf(this).toPence()
