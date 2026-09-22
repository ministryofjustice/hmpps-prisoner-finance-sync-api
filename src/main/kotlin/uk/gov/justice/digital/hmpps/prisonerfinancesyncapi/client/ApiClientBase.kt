package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.CustomException

open class ApiClientBase(val serviceName: String) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)!!
  }

  fun <T> handleExceptions(
    block: () -> T,
    message400: String = "Bad Request from $serviceName",
    message404: String = "Not found",
    message502: String = "Bad Gateway - $serviceName Unreachable or throwing an error",
    message500: String = "Unexpected Error",
    message409: String = "Conflict",
  ): T {
    try {
      return block()
    } catch (e: WebClientResponseException) {
      when {
        e.statusCode == HttpStatus.BAD_REQUEST && e.responseBodyAsString.contains("Page requested is out of range") ->
          throw CustomException(message = "Page requested is out of range", status = HttpStatus.BAD_REQUEST)

        e.statusCode == HttpStatus.BAD_REQUEST -> throw CustomException(message400, HttpStatus.BAD_REQUEST, e)

        e.statusCode == HttpStatus.NOT_FOUND -> throw CustomException(message404, HttpStatus.NOT_FOUND, e)

        e.statusCode == HttpStatus.INTERNAL_SERVER_ERROR -> throw CustomException(message502, HttpStatus.BAD_GATEWAY, e)

        e.statusCode == HttpStatus.CONFLICT -> throw CustomException(message409, HttpStatus.CONFLICT, e)

        else -> throw CustomException(message500, HttpStatus.INTERNAL_SERVER_ERROR, e)
      }
    }
  }
}
