package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.holds.HoldsControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.ReleaseHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.ReleasedHoldResponse
import java.util.UUID

@Component
class HoldsApiClient(
  private val holdsControllerApi: HoldsControllerApi,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  private fun <T> handleExceptions(
    block: () -> T,
    message400: String = "Bad Request from General Ledger",
    message404: String = "Not found",
    message502: String = "Bad Gateway - General Ledger Unreachable or throwing an error",
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

  @Throws(WebClientResponseException::class)
  fun postHold(request: CreateHoldRequest): HoldResponse {
    log.info("Creating Hold for hold number ${request.legacyHoldNumber} for prison number ${request.prisonNumber}")
    val response = handleExceptions(
      block = {
        holdsControllerApi.postHold(request)
          .block()
      },
    )

    return response ?: throw IllegalStateException("Received null response when creating hold ${request.legacyHoldNumber}")
  }

  @Throws(WebClientResponseException::class)
  fun postHoldRelease(holdsUUID: UUID, request: ReleaseHoldRequest): ReleasedHoldResponse {
    log.info("Releasing Hold for hold number $holdsUUID")
    val response = handleExceptions(
      block = {
        holdsControllerApi.releaseHoldById(
          id = holdsUUID,
          releaseHoldRequest = request,
        )
          .block()
      },
    )

    return response ?: throw IllegalStateException("Received null response when creating hold $holdsUUID")
  }
}
