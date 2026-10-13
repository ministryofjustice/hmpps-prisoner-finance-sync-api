package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.AdvancesApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AdvanceMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AdvancesMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRepayRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.util.UUID

@Service
class AdvancesService(
  @Autowired var advancesApiClient: AdvancesApiClient,
  @Autowired var timeConversionService: TimeConversionService,
  @Autowired var advancesMappingRepository: AdvancesMappingRepository,
  @Autowired val idempotencyService: GeneralLedgerIdempotencyService,
) {
  // we do not expect any advance to have more than one entry
  val advanceTransactionEntrySequence = 1

  fun createAdvance(syncCreateAdvanceRecordRequest: SyncCreateAdvanceRecordRequest): SyncCreateAdvanceRecordResponse {
    val mapping = advancesMappingRepository.findAdvanceMappingByLegacyPaymentProfileId(syncCreateAdvanceRecordRequest.legacyPaymentProfileId)

    if (mapping != null) {
      return SyncCreateAdvanceRecordResponse(
        paymentProfileId = mapping.legacyPaymentProfileId,
        advanceId = mapping.advanceUuid,
      )
    }

    val idempotencyKey = idempotencyService.genTransactionIdempotencyKey(
      syncCreateAdvanceRecordRequest.legacyPaymentProfileId,
      advanceTransactionEntrySequence,
    )

    val createAdvanceRecordRequest = CreateAdvanceRecordRequest(
      legacyPaymentProfileId = syncCreateAdvanceRecordRequest.legacyPaymentProfileId,
      legacyInformationNumber = syncCreateAdvanceRecordRequest.legacyInformationNumber,
      prisonNumber = syncCreateAdvanceRecordRequest.prisonNumber,
      prisonID = syncCreateAdvanceRecordRequest.prisonID,
      amount = syncCreateAdvanceRecordRequest.amount.toPence(),
      createdOn = timeConversionService.toUtcInstant(syncCreateAdvanceRecordRequest.createdOn),
      repaymentStartDate = timeConversionService.toUtcInstant(syncCreateAdvanceRecordRequest.repaymentStartDate),
      repaymentAmount = syncCreateAdvanceRecordRequest.repaymentAmount.toPence(),
      reference = syncCreateAdvanceRecordRequest.reference ?: "",
      createdBy = syncCreateAdvanceRecordRequest.createdBy,
      status = syncCreateAdvanceRecordRequest.status,
      comment = syncCreateAdvanceRecordRequest.comment,
      legacyTransactionId = syncCreateAdvanceRecordRequest.legacyTransactionId,
    )

    val response = advancesApiClient.postAdvanceRecord(createAdvanceRecordRequest, idempotencyKey)

    val advanceMapping = AdvanceMapping(
      legacyPaymentProfileId = syncCreateAdvanceRecordRequest.legacyPaymentProfileId,
      advanceUuid = response.id,
    )

    advancesMappingRepository.save(advanceMapping)

    val syncCreateAdvanceRecordResponse = SyncCreateAdvanceRecordResponse(
      paymentProfileId = syncCreateAdvanceRecordRequest.legacyPaymentProfileId,
      advanceId = advanceMapping.advanceUuid,
    )

    return syncCreateAdvanceRecordResponse
  }

  fun createAdvanceRepayment(advanceId: UUID, syncAdvanceRepayRequest: SyncCreateAdvanceRepayRequest) {
  }
}
