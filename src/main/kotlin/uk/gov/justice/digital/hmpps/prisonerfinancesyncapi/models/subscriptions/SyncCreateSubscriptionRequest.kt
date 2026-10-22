package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

data class SyncCreateSubscriptionRequest(
  val legacyPaymentProfileId: Long,
  val amount: BigDecimal,
  val dayOfPayment: DayOfWeek,
  val startDate: LocalDate,
  val endDate: LocalDate? = null,
  val subscriptionType: String,
  val reference: String? = null,
  val createdBy: String,
  val createdAt: Instant,
  val prisonId: String,
  val prisonerNumber: String,
  val subAccountCode: String,
)
