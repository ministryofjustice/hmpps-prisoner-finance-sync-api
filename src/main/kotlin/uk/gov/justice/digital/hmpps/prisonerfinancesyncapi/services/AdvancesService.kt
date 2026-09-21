package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.AdvancesApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AdvanceMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AdvancesMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence

@Service
class AdvancesService(
  var advancesApiClient: AdvancesApiClient,
  var timeConversionService: TimeConversionService,
  var advancesMappingRepository: AdvancesMappingRepository,
) {
  fun createAdvance(syncCreateAdvanceRecordRequest: SyncCreateAdvanceRecordRequest): SyncCreateAdvanceRecordResponse {
    val mapping = advancesMappingRepository.findAdvanceMappingByLegacyPaymentProfileId(syncCreateAdvanceRecordRequest.legacyPaymentProfileId)

    if (mapping != null) {
      return SyncCreateAdvanceRecordResponse(
        paymentProfileId = mapping.legacyPaymentProfileId,
        advanceUuid = mapping.advanceUuid,
      )
    }

    val createAdvanceRecordRequest = CreateAdvanceRecordRequest(
      legacyPaymentProfileId = syncCreateAdvanceRecordRequest.legacyPaymentProfileId,
      legacyInformationNumber = syncCreateAdvanceRecordRequest.legacyInformationNumber,
      prisonNumber = syncCreateAdvanceRecordRequest.prisonNumber,
      prisonID = syncCreateAdvanceRecordRequest.prisonID,
      amount = syncCreateAdvanceRecordRequest.amount.toPence(),
      createdOn = timeConversionService.toUtcInstant(syncCreateAdvanceRecordRequest.createdOn),
      repaymentStartDate = timeConversionService.toUtcInstant(syncCreateAdvanceRecordRequest.repaymentStartDate),
      repaymentAmount = syncCreateAdvanceRecordRequest.repaymentAmount.toPence(),
      reference = syncCreateAdvanceRecordRequest.reference,
      createdBy = syncCreateAdvanceRecordRequest.createdBy,
      status = syncCreateAdvanceRecordRequest.status,
    )

    val response = advancesApiClient.postAdvanceRecord(createAdvanceRecordRequest)

    val advanceMapping = AdvanceMapping(
      legacyPaymentProfileId = syncCreateAdvanceRecordRequest.legacyPaymentProfileId,
      advanceUuid = response.id,
    )

    advancesMappingRepository.save(advanceMapping)

    val syncCreateAdvanceRecordResponse = SyncCreateAdvanceRecordResponse(
      paymentProfileId = syncCreateAdvanceRecordRequest.legacyPaymentProfileId,
      advanceUuid = advanceMapping.advanceUuid,
    )

    return syncCreateAdvanceRecordResponse
  }
}
