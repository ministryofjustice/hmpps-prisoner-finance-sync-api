package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.advances.AdvanceRecordControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.advances.AdvanceRepaymentControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRepaymentRequest
import java.util.UUID

@Component
class AdvancesApiClient(
  private val advanceRecordControllerApi: AdvanceRecordControllerApi,
  private val advanceRepaymentControllerApi: AdvanceRepaymentControllerApi,
) : ApiClientBase("Advances API") {

  @Throws(WebClientResponseException::class, IllegalStateException::class)
  fun postAdvanceRecord(request: CreateAdvanceRecordRequest, idempotencyKey: UUID): AdvanceRecordResponse {
    log.info("Creating advance for advance number ${request.legacyPaymentProfileId} for prison number ${request.prisonNumber}")
    val response = handleExceptions(
      block = {
        advanceRecordControllerApi.postAdvanceRecord(idempotencyKey, request)
          .block()
      },
    )

    return response ?: throw IllegalStateException("Received null response when creating advance ${request.legacyPaymentProfileId}")
  }

  @Throws(WebClientResponseException::class, IllegalStateException::class)
  fun postAdvanceRepayment(advanceId: UUID, request: CreateAdvanceRepaymentRequest, idempotencyKey: UUID): AdvanceRepaymentResponse {
    log.info("Creating advance repayment for advance UUID $advanceId for amount ${request.amount}")
    val response = handleExceptions(
      block = {
        advanceRepaymentControllerApi.repayAdvance(idempotencyKey = idempotencyKey, advanceId = advanceId, createAdvanceRepaymentRequest = request)
          .block()
      },
    )

    return response ?: throw IllegalStateException("Received null response when creating advance repayment for advance $advanceId")
  }
}
