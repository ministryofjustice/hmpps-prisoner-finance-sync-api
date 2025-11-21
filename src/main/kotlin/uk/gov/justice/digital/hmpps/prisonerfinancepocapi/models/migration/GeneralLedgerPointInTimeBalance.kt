package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDateTime

@Schema(description = "Represents a single general ledger account and its balance at a specific point in time.")
data class GeneralLedgerPointInTimeBalance(
  @field:NotNull
  @field:Schema(description = "The account code for the general ledger account.")
  val accountCode: Int,

  @field:NotNull
  @field:Schema(description = "The account balance at the specified time.", example = "123.45")
  val balance: BigDecimal,

  @field:NotNull
  @field:Schema(
    description = "The local date and time from the legacy system when this balance was valid.",
    example = "2025-09-24T10:00:00",
  )
  val asOfTimestamp: LocalDateTime,
)
