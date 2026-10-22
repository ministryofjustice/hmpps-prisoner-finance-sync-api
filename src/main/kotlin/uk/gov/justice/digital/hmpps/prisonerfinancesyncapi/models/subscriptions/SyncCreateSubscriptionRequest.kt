package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

data class SyncCreateSubscriptionRequest(
  @field:Schema(description = "The payment profile ID", example = "123456789", required = true)
  val legacyPaymentProfileId: Long,
  @field:Schema(description = "The amount paid weekly in pence", example = "0.50", required = true)
  val amount: BigDecimal,
  @field:Schema(description = "The day the payment is made weekly", example = "MONDAY", required = true)
  val dayOfPayment: DayOfWeek,
  @field:Schema(description = "The start date of the subscription", example = "2026-12-03", required = true)
  val startDate: LocalDate,
  @field:Schema(description = "The end date of the subscription", example = "2026-13-04", required = false)
  val endDate: LocalDate? = null,
  @field:Schema(description = "The transaction type on the payment profile", example = "TELE", required = true)
  val subscriptionType: String,
  @field:Schema(description = "The reference on the payment profile", example = "REF", required = false)
  val reference: String? = null,
  @field:Schema(description = "The user that created the subscription", example = "TEST_USER", required = true)
  val createdBy: String,
  @field:Schema(description = "The time that the subscription was created", example = "2023-07-01T12:00:00Z", required = true)
  val createdAt: Instant,
  @field:Schema(description = "The caseload ID of the prison", example = "LEI", required = true)
  val prisonId: String,
  @field:Schema(description = "The prison number of the offender that has the subscription", example = "AE12345", required = true)
  val prisonerNumber: String,
  @field:Schema(description = "The sub-account that the subscription payment is debited from", example = "2101", required = true)
  val subAccountCode: Int,
)
