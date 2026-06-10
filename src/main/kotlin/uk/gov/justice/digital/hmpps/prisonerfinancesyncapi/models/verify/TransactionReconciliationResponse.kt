package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.verify

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SearchPostingResponse
import java.time.Instant
import java.util.UUID

data class TransactionReconciliationResponse(
  val nomisTransactionId: Long,
  val glTransactionId: UUID,
  val transactionCreatedAt: Instant,
  val postings: List<SearchPostingResponse>,
)

data class PagedResponse<T>(
  val content: List<T>,
  val pageNumber: Int,
  val pageSize: Int,
  val totalElements: Long,
  val totalPages: Int,
  val isLastPage: Boolean,
)
