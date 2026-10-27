package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.SubscriptionsApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.SubscriptionsMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.SubscriptionsMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.CreateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncCreateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncCreateSubscriptionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence

@Service
class SubscriptionsService(
  private val idempotencyService: IdempotencyService,
  private val timeConversionService: TimeConversionService,
  private val ledgerAccountMappingService: LedgerAccountMappingService,
  private val subscriptionsApiClient: SubscriptionsApiClient,
  private val subscriptionsMappingRepository: SubscriptionsMappingRepository,
) {

  fun createSubscription(syncSubscriptionRequest: SyncCreateSubscriptionRequest): SyncCreateSubscriptionResponse {
    val idempotencyKey = idempotencyService.genSubscriptionIdempotencyKey(syncSubscriptionRequest.legacyPaymentProfileId)
    val subAccountRef = ledgerAccountMappingService.mapPrisonerSubAccount(syncSubscriptionRequest.subAccountCode)

    val createSubscriptionRequest = CreateSubscriptionRequest(
      legacyPaymentProfileId = syncSubscriptionRequest.legacyPaymentProfileId,
      amount = syncSubscriptionRequest.amount.toPence(),
      dayOfPayment = CreateSubscriptionRequest.DayOfPayment.valueOf(syncSubscriptionRequest.dayOfPayment.name),
      startDate = syncSubscriptionRequest.startDate,
      subscriptionType = syncSubscriptionRequest.subscriptionType,
      createdAt = timeConversionService.toUtcInstant(syncSubscriptionRequest.createdAt),
      prisonId = syncSubscriptionRequest.prisonId,
      prisonNumber = syncSubscriptionRequest.prisonNumber,
      subAccountRef = CreateSubscriptionRequest.SubAccountRef.valueOf(subAccountRef),
      endDate = syncSubscriptionRequest.endDate,
      reference = syncSubscriptionRequest.reference,
      createdBy = syncSubscriptionRequest.createdBy,
    )

    val response = subscriptionsApiClient.postSubscription(createSubscriptionRequest, idempotencyKey)

    tryToSaveMapping(
      SubscriptionsMapping(
        legacyPaymentProfileId = syncSubscriptionRequest.legacyPaymentProfileId,
        subscriptionUuid = response.id,
      ),
    )

    return SyncCreateSubscriptionResponse(response.id)
  }

  fun tryToSaveMapping(subscriptionMapping: SubscriptionsMapping) {
    try {
      subscriptionsMappingRepository.save(subscriptionMapping)
    } catch (e: Exception) {
      if (e.message?.contains("duplicate key value violates unique constraint \"uc_subscriptions_mapping_subscription_uuid\"") == true ||
        e.message?.contains("duplicate key value violates unique constraint \"uc_subscriptions_mapping_legacy_payment_profile_id\"") == true
      ) {
        return
      }
      throw e
    }
  }
}
