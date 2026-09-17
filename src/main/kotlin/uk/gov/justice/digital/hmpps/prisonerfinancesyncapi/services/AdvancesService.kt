package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.AdvancesApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal

@Service
class AdvancesService(var advancesApiClient: AdvancesApiClient) {
  fun createAdvance(syncCreateAdvanceRecordRequest: SyncCreateAdvanceRecordRequest) : AdvanceRecordResponse {

    val createAdvanceRecordRequest = CreateAdvanceRecordRequest(
      legacyPaymentProfileId = syncCreateAdvanceRecordRequest.legacyPaymentProfileId,
      legacyInformationNumber = syncCreateAdvanceRecordRequest.legacyInformationNumber,
      prisonNumber = syncCreateAdvanceRecordRequest.prisonNumber,
      prisonID = syncCreateAdvanceRecordRequest.prisonID,
      amount = syncCreateAdvanceRecordRequest.amount.toPence(),
      createdOn = syncCreateAdvanceRecordRequest.createdOn,
      repaymentStartDate = syncCreateAdvanceRecordRequest.repaymentStartDate,
      repaymentAmount = syncCreateAdvanceRecordRequest.repaymentAmount.toPence(),
      reference = syncCreateAdvanceRecordRequest.reference,
      createdBy = syncCreateAdvanceRecordRequest.createdBy,
      status = syncCreateAdvanceRecordRequest.status,
    )

    val response = advancesApiClient.postAdvanceRecord(createAdvanceRecordRequest)

    return response
  }
}