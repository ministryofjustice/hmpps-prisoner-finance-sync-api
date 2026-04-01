package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import java.time.Instant
import java.util.UUID

@Service
class SyncPayloadCaptureService(
  private val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
  private val objectMapper: ObjectMapper,
  private val timeConversionService: TimeConversionService,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun <T : SyncRequest> captureAndStoreRequest(
    requestBodyObject: T,
    synchronizedTransactionId: UUID? = null,
  ): NomisSyncPayload {
    val rawBodyJson = safeSerializeRequest(requestBodyObject)

    var caseloadId: String? = null
    var transactionType = ""
    var requestTypeIdentifier: String?
    var transactionInstant: Instant? = null

    when (requestBodyObject) {
      is SyncOffenderTransactionRequest -> {
        caseloadId = requestBodyObject.caseloadId
        transactionType = requestBodyObject.offenderTransactions.firstOrNull()?.type ?: ""
        requestTypeIdentifier = SyncOffenderTransactionRequest::class.simpleName
        transactionInstant = timeConversionService.toUtcInstant(requestBodyObject.transactionTimestamp)
      }
      is SyncGeneralLedgerTransactionRequest -> {
        caseloadId = requestBodyObject.caseloadId
        transactionType = requestBodyObject.transactionType
        requestTypeIdentifier = SyncGeneralLedgerTransactionRequest::class.simpleName
        transactionInstant = timeConversionService.toUtcInstant(requestBodyObject.transactionTimestamp)
      }
      else -> {
        requestTypeIdentifier = requestBodyObject::class.simpleName
        log.warn("Unrecognized request body type for capture: ${requestBodyObject::class.simpleName}. Storing with generic identifier.")
      }
    }

    val payload = NomisSyncPayload(
      timestamp = Instant.now(),
      legacyTransactionId = requestBodyObject.transactionId,
      synchronizedTransactionId = synchronizedTransactionId ?: UUID.randomUUID(),
      requestId = requestBodyObject.requestId,
      caseloadId = caseloadId,
      requestTypeIdentifier = requestTypeIdentifier,
      body = rawBodyJson,
      transactionType = transactionType,
      transactionTimestamp = transactionInstant,
    )
    return nomisSyncPayloadRepository.save(payload)
  }

  private fun safeSerializeRequest(requestBodyObject: Any): String = try {
    objectMapper.writeValueAsString(requestBodyObject)
  } catch (e: Exception) {
    log.error("Could not serialize request body to JSON for capture. Type: ${requestBodyObject::class.simpleName}", e)
    "{}"
  }
}
