package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.verify

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SearchPostingResponse
import java.time.Instant
import java.util.UUID

data class TransactionReconciliationResponse(
  val nomisTransactionId: Long,
  val glTransactionId: UUID,
  val transactionCreatedAt: Instant,
  val postings: List<SearchPostingResponse>,
  // stuff we might need
  // caseloadId,
  // in the postings
  // subaccountCode
  // prisonNumber == account reference
  // offenderBookingId
)
data class DailyReconciliationResponse(
  val transactions: List<TransactionReconciliationResponse>,
)
