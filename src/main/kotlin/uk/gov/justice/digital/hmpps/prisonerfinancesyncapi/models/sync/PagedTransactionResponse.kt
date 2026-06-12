package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync

class PagedTransactionResponse(
  val transactions: List<SyncGeneralLedgerTransactionResponse>,
  val pageNumber: Int,
  val pageSize: Int,
  val totalElements: Long,
  val totalPages: Int,
  val isLastPage: Boolean,
)
