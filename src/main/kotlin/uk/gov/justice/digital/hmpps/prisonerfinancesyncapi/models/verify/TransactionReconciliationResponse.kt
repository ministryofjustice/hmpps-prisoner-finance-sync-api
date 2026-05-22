package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.verify

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingResponse
import java.time.Instant
import java.util.UUID

data class TransactionReconciliationResponse(
  val nomisTransactionId: Long,
  val glTransactionId: UUID,
  val transactionCreatedAt: Instant,
  val postings: List<PostingResponse>,
  // stuff we actually need
  // caseloadId,
  // in the postings
  // subaccountCode
  // prisonNumber == account reference
  // offenderBookingId
)

// note: we will need to create another mapping peer sub account that maps to offenderBookingId + subacountCode

data class DailyReconciliationResponse(
  val transactions: List<TransactionReconciliationResponse>,
)
