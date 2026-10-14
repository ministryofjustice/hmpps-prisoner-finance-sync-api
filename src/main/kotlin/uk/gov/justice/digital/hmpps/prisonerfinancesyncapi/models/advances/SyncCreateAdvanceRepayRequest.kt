package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Pattern
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_MESSAGE_PRISON_ID
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_REGEX_PRISON_ID
import java.math.BigDecimal
import java.time.LocalDateTime

data class SyncCreateAdvanceRepayRequest(

  @field:Schema(description = "The amount paid towards the advance", example = "5.00", required = true)
  @field:Digits(integer = 19, fraction = 2)
  val amount: BigDecimal,

  @field:Schema(description = "The transaction id that paid the amount to the advance", example = "123456", required = true)
  val transactionId: Long,

  @field:Schema(description = "The transaction date and time for the advance repayment", example = "2024-06-18T00:00:00.000000", required = true)
  val createdAt: LocalDateTime,

  @field:Schema(description = "The username that created this advance repayment", example = "JOHN_USER", required = true)
  val createdBy: String,

  @field:Schema(description = "The description on the advance repayment transaction", example = "Deduction-ADV-08052026 D D", required = true)
  val description: String,

  @field:Schema(description = "The entry sequence from the offender transactions table in NOMIS", example = "1", required = true)
  val entrySequence: Int,

  @field:Pattern(
    regexp = VALIDATION_REGEX_PRISON_ID,
    message = VALIDATION_MESSAGE_PRISON_ID,
  )
  @field:Schema(description = "The caseload ID for the prison which receives the payment", example = "LEI", required = true)
  val caseloadId: String,
)
