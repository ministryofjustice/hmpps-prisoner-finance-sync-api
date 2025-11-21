package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

data class SyncOffenderTransactionListResponse(
  @field:ArraySchema(
    arraySchema = Schema(description = "The list of offender transactions on the current page"),
    schema = Schema(implementation = SyncOffenderTransactionResponse::class),
  )
  val offenderTransactions: List<SyncOffenderTransactionResponse>,

  @field:Schema(description = "The current page number (0-indexed).")
  val page: Int,

  @field:Schema(description = "The total number of elements across all pages.")
  val totalElements: Long,

  @field:Schema(description = "The total number of pages.")
  val totalPages: Int,

  @field:Schema(description = "Indicates if this is the last page.")
  val last: Boolean,
)
