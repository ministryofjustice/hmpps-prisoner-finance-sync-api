package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config

import org.springframework.http.HttpStatusCode

class CustomException(override val message: String, val status: HttpStatusCode, override val cause: Exception? = null) : Exception(message)
