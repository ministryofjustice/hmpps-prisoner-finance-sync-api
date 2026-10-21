package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.AdvancesApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AdvanceMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AdvanceRepaymentMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AdvanceRepaymentMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AdvancesMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceWriteOffRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncAdvanceRepayResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncAdvanceWriteOffResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRepayRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceWriteOffRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.util.UUID

@Service
class AdvancesService(
  @Autowired var advancesApiClient: AdvancesApiClient,
  @Autowired var timeConversionService: TimeConversionService,
  @Autowired var advancesMappingRepository: AdvancesMappingRepository,
  @Autowired val idempotencyService: GeneralLedgerIdempotencyService,
  @Autowired val advanceRepaymentMappingRepository: AdvanceRepaymentMappingRepository,
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

  fun createAdvanceRepayment(advanceId: UUID, syncAdvanceRepayRequest: SyncCreateAdvanceRepayRequest): SyncAdvanceRepayResponse {
    val generatedIdempotency = idempotencyService.genTransactionIdempotencyKey(
      transactionId = syncAdvanceRepayRequest.transactionId,
      entrySequence = syncAdvanceRepayRequest.entrySequence,
    )

    val createAdvanceRepayRequest = CreateAdvanceRepaymentRequest(
      amount = syncAdvanceRepayRequest.amount.toPence(),
      createdAt = timeConversionService.toUtcInstant(syncAdvanceRepayRequest.createdAt),
      createdBy = syncAdvanceRepayRequest.createdBy,
      description = syncAdvanceRepayRequest.description,
      legacyTransactionId = syncAdvanceRepayRequest.transactionId,
      entrySequence = syncAdvanceRepayRequest.entrySequence,
    )

    val advanceRepayResponse = advancesApiClient.postAdvanceRepayment(
      advanceId = advanceId,
      idempotencyKey = generatedIdempotency,
      request = createAdvanceRepayRequest,
    )

    val advanceRepaymentMapping = AdvanceRepaymentMapping(
      legacyTransactionId = syncAdvanceRepayRequest.transactionId,
      entrySequence = syncAdvanceRepayRequest.entrySequence,
      generalLedgerTransactionUuid = advanceRepayResponse.transactionId,
    )

    tryToSaveAdvanceRepaymentMapping(advanceRepaymentMapping)

    return SyncAdvanceRepayResponse(
      repaymentTransactionId = advanceRepayResponse.transactionId,
    )
  }

  private fun tryToSaveAdvanceRepaymentMapping(advanceRepaymentMapping: AdvanceRepaymentMapping) {
    try {
      advanceRepaymentMappingRepository.save(advanceRepaymentMapping)
    } catch (e: DataIntegrityViolationException) {
      // ignore the exception if the mapping already exists
      if (e.message?.contains("duplicate key value violates unique constraint \"uc_legacy_transaction_id_and_entry_sequence\"") == true) {
        return
      }
      throw e
    }
  }

  fun writeOffAdvance(advanceId: UUID, syncAdvanceWriteOffRequest: SyncCreateAdvanceWriteOffRequest): SyncAdvanceWriteOffResponse {
    val advanceResponse = advancesApiClient.postWriteOffAdvance(
      advanceId = advanceId,
      request = AdvanceWriteOffRequest(
        writeOffDateTime = timeConversionService.toUtcInstant(syncAdvanceWriteOffRequest.writeOffDateTime),
        writtenOffBy = syncAdvanceWriteOffRequest.writtenOffBy,
        writtenOffAmount = syncAdvanceWriteOffRequest.writtenOffAmount.toPence(),
      ),
    )

    return SyncAdvanceWriteOffResponse(
      advanceId = advanceResponse.id,
      advanceStatus = advanceResponse.status,
      writtenOffAmount = syncAdvanceWriteOffRequest.writtenOffAmount,
    )
  }
}
