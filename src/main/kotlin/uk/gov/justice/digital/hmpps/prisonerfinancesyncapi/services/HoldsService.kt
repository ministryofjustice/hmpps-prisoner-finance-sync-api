package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.HoldsApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.HoldsMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.GeneralLedgerTransactionMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.HoldsMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.CreateHoldMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.ReleaseHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncReleaseHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncReleasedHoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPounds
import java.util.UUID

@Service
class HoldsService(
  var timeConversionService: TimeConversionService,
  var holdsApiClient: HoldsApiClient,
  var holdsMappingRepository: HoldsMappingRepository,
  val idempotencyService: GeneralLedgerIdempotencyService,
  val accountResolver: GeneralLedgerAccountResolver,
  val requestCache: InMemoryAccountCache = InMemoryAccountCache(),
  val accountMapping: LedgerAccountMappingService,
  val generalLedgerTransactionMappingRepository: GeneralLedgerTransactionMappingRepository,
) {
  // HARDCODED, the only hold account code used in NOMIS as far as we know
  val holdNOMISAccountCode = 2199

  fun createHold(syncCreateHoldRequest: SyncCreateHoldRequest): SyncCreateHoldResponse {
    val mapping = holdsMappingRepository.findHoldsMappingByLegacyHoldNumber(syncCreateHoldRequest.holdNumber)

    if (mapping != null) {
      return SyncCreateHoldResponse(mapping.legacyHoldNumber, mapping.holdsUuid)
    }

    val prisonSubAccountId = accountResolver.resolveSubAccount(
      prisonId = syncCreateHoldRequest.holdLocation,
      offenderId = "",
      accountCode = holdNOMISAccountCode,
      transactionType = syncCreateHoldRequest.holdType,
      parentCache = requestCache,
    )

    val prisonerSubAccountId = accountResolver.resolveSubAccount(
      prisonId = "",
      offenderId = syncCreateHoldRequest.prisonNumber,
      accountCode = syncCreateHoldRequest.subAccountCode,
      transactionType = syncCreateHoldRequest.holdType,
      parentCache = requestCache,
    )

    val subAccountRef = accountMapping.mapPrisonerSubAccount(
      syncCreateHoldRequest.subAccountCode,
    )

    val createHoldRequest = syncCreateHoldRequest.toCreateHoldRequest(
      subAccountRef = CreateHoldRequest.SubAccountRef.valueOf(subAccountRef),
      createdAt = timeConversionService.toUtcInstant(syncCreateHoldRequest.createdAt),
      holdFromDate = timeConversionService.toUtcInstant(syncCreateHoldRequest.holdFromDate),
      holdUntilDate = if (syncCreateHoldRequest.holdUntilDate != null) {
        timeConversionService.toUtcInstant(syncCreateHoldRequest.holdUntilDate)
      } else {
        null
      },
      prisonSubAccountId = prisonSubAccountId,
      prisonerSubAccountId = prisonerSubAccountId,
    )

    val idempotencyKey = idempotencyService.genTransactionIdempotencyKey(
      transactionId = syncCreateHoldRequest.holdTransactionId,
      // HARDCODED, we do not expect any hold transaction to have more than one entry
      entrySequence = 1,
    )

    val response = holdsApiClient.postHold(createHoldRequest, idempotencyKey = idempotencyKey)

    val holdsMapping = HoldsMapping(legacyHoldNumber = syncCreateHoldRequest.holdNumber, holdsUuid = response.id)

    holdsMappingRepository.save(holdsMapping)

    val syncCreateHoldResponse = SyncCreateHoldResponse(createHoldRequest.legacyHoldNumber, response.id)

    return syncCreateHoldResponse
  }

  private fun getOneTransactionMappingByLegacyId(legacyTransactionId: Long): UUID? {
    val transaction = generalLedgerTransactionMappingRepository.findGeneralLedgerTransactionMappingByLegacyTransactionId(
      legacyTransactionId,
    )

    if (transaction.size > 1) {
      throw IllegalStateException("Unexpected more than one transaction found for transaction id $legacyTransactionId")
    }

    return transaction.firstOrNull()?.glTransactionUuid
  }

  fun migrateHold(syncCreateHoldRequest: SyncCreateHoldRequest): SyncCreateHoldResponse {
    val subAccountRef = accountMapping.mapPrisonerSubAccount(
      syncCreateHoldRequest.subAccountCode,
    )

    val holdTransactionGlId = getOneTransactionMappingByLegacyId(syncCreateHoldRequest.holdTransactionId)
    val releasedTransactionGlId = if (syncCreateHoldRequest.releaseTransactionId != null) {
      getOneTransactionMappingByLegacyId(syncCreateHoldRequest.releaseTransactionId)
    } else {
      null
    }

    val createHoldMigrationRequest = CreateHoldMigrationRequest(
      prisonNumber = syncCreateHoldRequest.prisonNumber,
      legacyHoldNumber = syncCreateHoldRequest.holdNumber,
      subAccountRef = CreateHoldMigrationRequest.SubAccountRef.valueOf(subAccountRef),
      createdAt = timeConversionService.toUtcInstant(syncCreateHoldRequest.createdAt),
      holdFromDate = timeConversionService.toUtcInstant(syncCreateHoldRequest.holdFromDate),
      holdUntilDate = if (syncCreateHoldRequest.holdUntilDate != null) {
        timeConversionService.toUtcInstant(syncCreateHoldRequest.holdUntilDate)
      } else {
        null
      },
      createdBy = syncCreateHoldRequest.createdBy,
      isReleased = syncCreateHoldRequest.isReleased,
      holdType = CreateHoldMigrationRequest.HoldType.valueOf(syncCreateHoldRequest.holdType),
      amount = syncCreateHoldRequest.amount.toPence(),
      holdLocation = syncCreateHoldRequest.holdLocation,
      description = syncCreateHoldRequest.description,
      holdTransactionId = holdTransactionGlId,
      releasedTransactionId = releasedTransactionGlId,
    )

    val response = holdsApiClient.migrateHold(createHoldMigrationRequest)

    val holdsMapping = HoldsMapping(legacyHoldNumber = syncCreateHoldRequest.holdNumber, holdsUuid = response.id)

    holdsMappingRepository.save(holdsMapping)

    val syncCreateHoldResponse = SyncCreateHoldResponse(createHoldMigrationRequest.legacyHoldNumber, response.id)

    return syncCreateHoldResponse
  }

  fun releaseHold(holdNumber: Long, releaseRequest: SyncReleaseHoldRequest): SyncReleasedHoldResponse {
    val mapping = holdsMappingRepository.findHoldsMappingByLegacyHoldNumber(holdNumber) ?: throw CustomException("No hold mapping found for hold number: $holdNumber", HttpStatus.NOT_FOUND)

    val releaseHoldRequest = ReleaseHoldRequest(
      releaseDateTime = timeConversionService.toUtcInstant(releaseRequest.releaseDateTime),
    )
    val response = holdsApiClient.postHoldRelease(mapping.holdsUuid, releaseHoldRequest)

    return SyncReleasedHoldResponse(
      prisonNumber = response.prisonNumber,
      holdNumber = holdNumber,
      amountReleased = response.amountReleased.toPounds(),
      releasedAt = timeConversionService.toLocalDateTime(response.releasedAt),
    )
  }
}
