package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils

import java.math.BigDecimal

fun Long.toPounds(): BigDecimal = this.toBigDecimal().movePointLeft(2)
