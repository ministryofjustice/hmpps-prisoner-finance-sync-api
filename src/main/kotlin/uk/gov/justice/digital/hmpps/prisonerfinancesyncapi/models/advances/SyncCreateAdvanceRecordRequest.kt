package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

data class SyncCreateAdvanceRecordRequest(

  @field:Schema(description = "The payment profile id from NOMIS", example = "123456789", required = true)
  val legacyPaymentProfileId: String,

  @field:Schema(description = "The information number for the advance from NOMIS", example = "12345678-1", required = true)
  val legacyInformationNumber: String,

  @field:Schema(description = "The prison number of the offender this advance was for", example = "A9917EC", required = true)
  val prisonNumber: String,

  @field:Schema(description = "The prison ID that issued this advance", example = "LEI", required = true)
  val prisonID: String,

  @field:Schema(description = "The amount issued in the advance", example = "5.32", required = true)
  val amount: BigDecimal,

  @field:Schema(description = "The date time when the advance was created with the time set to midnight", example = "2024-06-18T00:00:00.000000", required = true)
  val createdOn: LocalDateTime,

  @field:Schema(description = "The date time when the payments are intended to begin", example = "2024-06-18T00:00:00.000000", required = true)
  val repaymentStartDate: LocalDateTime,

  @field:Schema(description = "The amount to be repaid weekly", example = "0.50", required = true)
  val repaymentAmount: BigDecimal,

  @field:Schema(description = "The reference from the payment profile ", example = "FNC", required = true)
  val reference: String,

  @field:Schema(description = "The username that created this advance", example = "JOHN_USER", required = true)
  val createdBy: String,

  @field:Schema(description = "The current status of this advance", example = "AdvanceStatus.ACTIVE", required = true)
  val status: CreateAdvanceRecordRequest.Status,

)
