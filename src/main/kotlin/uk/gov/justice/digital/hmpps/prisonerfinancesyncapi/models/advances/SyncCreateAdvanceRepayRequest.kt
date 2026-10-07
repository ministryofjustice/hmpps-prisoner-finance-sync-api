package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

data class SyncCreateAdvanceRepayRequest(

  @field:Schema(description = "The amount paid towards the advance", example = "5.00", required = true)
  val amount: BigDecimal,

  @field:Schema(description = "The transaction id that paid the amount to the advance", example = "123456", required = true)
  val transactionId: Long,

  @field:Schema(description = "The transaction date and time for the advance repayment", example = "2024-06-18T00:00:00.000000", required = true)
  val createdAt: LocalDateTime,

  @field:Schema(description = "The username that created this advance repayment", example = "JOHN_USER", required = true)
  val createdBy: String,
)
