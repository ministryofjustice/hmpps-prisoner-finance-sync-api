package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config

import org.springframework.http.HttpStatus

class CustomException(message: String, val status: HttpStatus) : Exception(message)
