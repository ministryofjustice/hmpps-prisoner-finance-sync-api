package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.holds.HoldsControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.holds.HoldsMigrationControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.CreateHoldMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.HoldBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.ReleaseHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.ReleasedHoldResponse
import java.util.UUID

@Component
class HoldsApiClient(
  private val holdsControllerApi: HoldsControllerApi,
  private val holdMigrationControllerApi: HoldsMigrationControllerApi,
) : ApiClientBase("Holds API") {

  @Throws(WebClientResponseException::class)
  fun postHold(request: CreateHoldRequest, idempotencyKey: UUID): HoldResponse {
    log.info("Creating Hold for hold number ${request.legacyHoldNumber} for prison number ${request.prisonNumber}")
    val response = handleExceptions(
      block = {
        holdsControllerApi.postHold(idempotencyKey = idempotencyKey, createHoldRequest = request)
          .block()
      },
    )

    return response ?: throw IllegalStateException("Received null response when creating hold ${request.legacyHoldNumber}")
  }

  @Throws(WebClientResponseException::class)
  fun migrateHold(request: CreateHoldMigrationRequest): HoldResponse {
    log.info("Creating Hold for hold number ${request.legacyHoldNumber} for prison number ${request.prisonNumber}")
    val response = handleExceptions(
      block = {
        holdMigrationControllerApi.migrateHold(request)
          .block()
      },
    )

    return response ?: throw IllegalStateException("Received null response when creating hold ${request.legacyHoldNumber}")
  }

  @Throws(WebClientResponseException::class)
  fun postHoldRelease(holdsUUID: UUID, request: ReleaseHoldRequest): ReleasedHoldResponse {
    log.info("Releasing Hold for hold ID $holdsUUID")
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

  @Throws(WebClientResponseException::class)
  fun getSubAccountHoldBalance(prisonNumber: String, subAccountReference: String): HoldBalanceResponse {
    val response = handleExceptions(
      block = {
        holdsControllerApi.getSubAccountHoldBalance(prisonNumber, subAccountReference)
          .block()
      },
    )
    return response ?: throw IllegalStateException("Received null response requesting hold balance for  $prisonNumber $subAccountReference")
  }
}
