package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.apache.hc.core5.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.HoldsApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.HoldsMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.HoldsMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.CreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence

@Service
class HoldsService (
  var timeConversionService: TimeConversionService,
  var holdsApiClient: HoldsApiClient,
  var holdsMappingRepository: HoldsMappingRepository) {

  fun mapSubAccountCodeToSubAccountRef(code: String) : CreateHoldRequest.SubAccountRef {
    return when (code) {
      "2101" -> CreateHoldRequest.SubAccountRef.CASH
      "2102" -> CreateHoldRequest.SubAccountRef.SPENDS
      "2103" -> CreateHoldRequest.SubAccountRef.SAVINGS
      else -> throw CustomException("Unexpected account code", HttpStatusCode.valueOf(400))
    }
  }

  fun mapHoldType(holdType: String) = CreateHoldRequest.HoldType.valueOf(holdType.uppercase())

  fun createHold(syncCreateHoldRequest: SyncCreateHoldRequest) : HoldResponse {

    val createHoldRequest = CreateHoldRequest(
      prisonNumber = syncCreateHoldRequest.prisonNumber,
      legacyHoldNumber = syncCreateHoldRequest.holdNumber,
      subAccountRef = mapSubAccountCodeToSubAccountRef(syncCreateHoldRequest.subAccountCode),
      createdAt = timeConversionService.toUtcInstant(syncCreateHoldRequest.createdAt),
      createdBy = syncCreateHoldRequest.createdBy,
      holdFromDate = timeConversionService.toUtcInstant(syncCreateHoldRequest.holdFromDate),
      isReleased = syncCreateHoldRequest.isReleased,
      holdType = mapHoldType(syncCreateHoldRequest.holdType),
      amount = syncCreateHoldRequest.amount.toPence().toLong(),
      holdLocation = syncCreateHoldRequest.holdLocation,
      holdUntilDate = if (syncCreateHoldRequest.holdUntilDate != null) timeConversionService.toUtcInstant(syncCreateHoldRequest.holdUntilDate) else null,
      description = syncCreateHoldRequest.description
    )

    val response = holdsApiClient.postHolds(createHoldRequest)

    val holdsMapping = HoldsMapping(legacyHoldNumber = syncCreateHoldRequest.holdNumber, holdsUuid = response.id)

    holdsMappingRepository.save(holdsMapping)

    return response

  }
}