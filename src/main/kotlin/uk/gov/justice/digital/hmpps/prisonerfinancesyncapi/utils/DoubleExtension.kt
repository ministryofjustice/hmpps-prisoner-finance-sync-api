package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils

fun Double.toGLLong(): Long = (this * 100).toLong()
