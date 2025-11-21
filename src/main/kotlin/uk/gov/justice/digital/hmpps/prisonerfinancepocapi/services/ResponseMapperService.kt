package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.SyncOffenderTransactionResponse

@Service
class ResponseMapperService {

  private val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerModule(KotlinModule.Builder().build())

  fun mapToGeneralLedgerTransactionResponse(payload: NomisSyncPayload): SyncGeneralLedgerTransactionResponse {
    val result = objectMapper.readValue<SyncGeneralLedgerTransactionRequest>(payload.body)
    return SyncGeneralLedgerTransactionResponse(
      synchronizedTransactionId = payload.synchronizedTransactionId,
      legacyTransactionId = payload.legacyTransactionId,
      description = result.description,
      reference = result.reference,
      caseloadId = result.caseloadId,
      transactionType = result.transactionType,
      transactionTimestamp = result.transactionTimestamp,
      createdAt = result.createdAt,
      createdBy = result.createdBy,
      createdByDisplayName = result.createdByDisplayName,
      lastModifiedAt = result.lastModifiedAt,
      lastModifiedBy = result.lastModifiedBy,
      lastModifiedByDisplayName = result.lastModifiedByDisplayName,
      generalLedgerEntries = result.generalLedgerEntries,
    )
  }

  fun mapToOffenderTransactionResponse(payload: NomisSyncPayload): SyncOffenderTransactionResponse {
    val result = objectMapper.readValue<SyncOffenderTransactionRequest>(payload.body)
    return SyncOffenderTransactionResponse(
      synchronizedTransactionId = payload.synchronizedTransactionId,
      legacyTransactionId = payload.legacyTransactionId,
      caseloadId = result.caseloadId,
      transactionTimestamp = result.transactionTimestamp,
      createdAt = result.createdAt,
      createdBy = result.createdBy,
      createdByDisplayName = result.createdByDisplayName,
      lastModifiedAt = result.lastModifiedAt,
      lastModifiedBy = result.lastModifiedBy,
      lastModifiedByDisplayName = result.lastModifiedByDisplayName,
      transactions = result.offenderTransactions,
    )
  }
}
