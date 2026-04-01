package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.MigratedGeneralLedgerBalancePayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.MigratedPrisonerBalancePayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.MigratedGeneralLedgerBalancePayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.MigratedPrisonerBalancePayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import java.time.Instant

@Service
class MigrationPayloadCaptureService(
  private val generalLedgerPayloadRepository: MigratedGeneralLedgerBalancePayloadRepository,
  private val prisonerBalancePayloadRepository: MigratedPrisonerBalancePayloadRepository,
  private val objectMapper: ObjectMapper,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
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
