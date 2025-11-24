package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A response containing a list of general ledger transactions.")
data class SyncGeneralLedgerTransactionListResponse(
  @field:ArraySchema(
    arraySchema = Schema(description = "The list of general ledger transactions on the current page"),
    schema = Schema(implementation = SyncGeneralLedgerTransactionResponse::class),
  )
  val transactions: List<SyncGeneralLedgerTransactionResponse>,

  @field:Schema(description = "The current page number (0-indexed).")
  val page: Int,

  @field:Schema(description = "The total number of elements across all pages.")
  val totalElements: Long,

  @field:Schema(description = "The total number of pages.")
  val totalPages: Int,

  @field:Schema(description = "Indicates if this is the last page.")
  val last: Boolean,

)
