package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.advances.AdvanceRecordControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest

@Component
class AdvancesApiClient(
  private val advanceRecordControllerApi: AdvanceRecordControllerApi,
) : ApiClientBase("Advances API") {

  @Throws(WebClientResponseException::class)
  fun postAdvanceRecord(request: CreateAdvanceRecordRequest): AdvanceRecordResponse {
    log.info("Creating advance for advance number ${request.legacyPaymentProfileId} for prison number ${request.prisonNumber}")
    val response = handleExceptions(
      block = {
        advanceRecordControllerApi.postAdvanceRecord(request)
          .block()
      },
    )

    return response ?: throw IllegalStateException("Received null response when creating advance ${request.legacyPaymentProfileId}")
  }
}
