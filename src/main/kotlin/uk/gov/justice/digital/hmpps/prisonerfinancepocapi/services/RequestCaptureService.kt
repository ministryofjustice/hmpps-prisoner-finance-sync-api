package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities.MigratedGeneralLedgerBalancePayload
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities.MigratedPrisonerBalancePayload
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.repositories.MigratedGeneralLedgerBalancePayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.repositories.MigratedPrisonerBalancePayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync.SyncRequest
import java.time.Instant
import java.util.UUID

@Service
class RequestCaptureService(
  private val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
  private val objectMapper: ObjectMapper,
  private val timeConversionService: TimeConversionService,
  private val generalLedgerPayloadRepository: MigratedGeneralLedgerBalancePayloadRepository,
  private val prisonerBalancePayloadRepository: MigratedPrisonerBalancePayloadRepository,
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
    var requestTypeIdentifier: String?
    var transactionInstant: Instant? = null

    when (requestBodyObject) {
      is SyncOffenderTransactionRequest -> {
        caseloadId = requestBodyObject.caseloadId
        requestTypeIdentifier = SyncOffenderTransactionRequest::class.simpleName
        transactionInstant = timeConversionService.toUtcInstant(requestBodyObject.transactionTimestamp)
      }
      is SyncGeneralLedgerTransactionRequest -> {
        caseloadId = requestBodyObject.caseloadId
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
      transactionTimestamp = transactionInstant,
    )
    return nomisSyncPayloadRepository.save(payload)
  }

  fun captureGeneralLedgerMigrationRequest(
    prisonId: String,
    request: GeneralLedgerBalancesSyncRequest,
  ): MigratedGeneralLedgerBalancePayload {
    val rawBodyJson = safeSerializeRequest(request)

    val payload = MigratedGeneralLedgerBalancePayload(
      prisonId = prisonId,
      timestamp = Instant.now(),
      body = rawBodyJson,
    )
    return generalLedgerPayloadRepository.save(payload)
  }

  fun capturePrisonerMigrationRequest(
    prisonNumber: String,
    request: PrisonerBalancesSyncRequest,
  ): MigratedPrisonerBalancePayload {
    val rawBodyJson = safeSerializeRequest(request)

    val payload = MigratedPrisonerBalancePayload(
      prisonNumber = prisonNumber,
      timestamp = Instant.now(),
      body = rawBodyJson,
    )
    return prisonerBalancePayloadRepository.save(payload)
  }

  private fun safeSerializeRequest(requestBodyObject: Any): String = try {
    objectMapper.writeValueAsString(requestBodyObject)
  } catch (e: Exception) {
    log.error("Could not serialize request body to JSON for capture. Type: ${requestBodyObject::class.simpleName}", e)
    "{}"
  }
}
