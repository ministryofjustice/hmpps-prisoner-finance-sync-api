package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.SubscriptionsApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.CreateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncCreateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncCreateSubscriptionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence

@Service
class SubscriptionsService(
  private val generalLedgerIdempotencyService: GeneralLedgerIdempotencyService,
  private val timeConversionService: TimeConversionService,
  private val ledgerAccountMappingService: LedgerAccountMappingService,
  private val subscriptionsApiClient: SubscriptionsApiClient,
) {
  val entrySequence = 1

  fun createSubscription(syncSubscriptionRequest: SyncCreateSubscriptionRequest): SyncCreateSubscriptionResponse {
    val idempotencyKey = generalLedgerIdempotencyService.genTransactionIdempotencyKey(syncSubscriptionRequest.legacyPaymentProfileId, entrySequence)
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

    return SyncCreateSubscriptionResponse(response.id)
  }
}
