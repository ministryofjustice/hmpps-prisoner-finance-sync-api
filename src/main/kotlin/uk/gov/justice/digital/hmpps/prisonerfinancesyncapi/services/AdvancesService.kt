package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.beans.factory.annotation.Autowired
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
  @Autowired var advancesApiClient: AdvancesApiClient,
  @Autowired var timeConversionService: TimeConversionService,
  @Autowired var advancesMappingRepository: AdvancesMappingRepository,
  @Autowired val idempotencyService: GeneralLedgerIdempotencyService,
  @Autowired val accountResolver: GeneralLedgerAccountResolver,
  @Autowired val requestCache: InMemoryAccountCache = InMemoryAccountCache(),
) {
  // HARDCODED values based on NOMIS data
  // the only advances account code used in NOMIS as far as we know
  val advancesNOMISAccountCode = 1502

  // we expect all advances to be sent to the Spends account
  val prisonerAdvanceAccountCode = 2102

  // we do not expect any advance to have more than one entry
  val advanceTransactionEntrySequence = 1

  fun createAdvance(syncCreateAdvanceRecordRequest: SyncCreateAdvanceRecordRequest): SyncCreateAdvanceRecordResponse {
    val mapping = advancesMappingRepository.findAdvanceMappingByLegacyPaymentProfileId(syncCreateAdvanceRecordRequest.legacyPaymentProfileId)

    if (mapping != null) {
      return SyncCreateAdvanceRecordResponse(
        paymentProfileId = mapping.legacyPaymentProfileId,
        advanceUuid = mapping.advanceUuid,
      )
    }

    val prisonSubAccountId = accountResolver.resolveSubAccount(
      prisonId = syncCreateAdvanceRecordRequest.prisonID,
      offenderId = "",
      accountCode = advancesNOMISAccountCode,
      transactionType = "ADV",
      parentCache = requestCache,
    )

    val prisonerSubAccountId = accountResolver.resolveSubAccount(
      prisonId = "",
      offenderId = syncCreateAdvanceRecordRequest.prisonNumber,
      accountCode = prisonerAdvanceAccountCode,
      transactionType = "ADV",
      parentCache = requestCache,
    )

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
      prisonerSubAccountId = prisonerSubAccountId,
      prisonSubAccountId = prisonSubAccountId,
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
      advanceUuid = advanceMapping.advanceUuid,
    )

    return syncCreateAdvanceRecordResponse
  }
}
