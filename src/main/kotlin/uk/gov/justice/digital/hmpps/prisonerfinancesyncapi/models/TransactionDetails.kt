package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import java.math.BigDecimal

data class TransactionDetailsList(val items: List<TransactionDetails>)

data class TransactionDetails(
  val id: String,
  val date: String,
  val type: String,
  val description: String? = null,
  val reference: String? = null,
  val clientRequestId: String? = null,
  val postings: List<TransactionPosting>,
) {

  data class TransactionPosting(
    val account: TransactionAccountDetails? = null,
    val address: String? = null,
    val postingType: PostingType,
    val amount: BigDecimal,
  )

  data class TransactionAccountDetails(
    val code: Int,
    val name: String,
    val transactionType: String?,
    val transactionDescription: String?,
    val prison: String?,
    val prisoner: String?,
    val classification: String,
    val postingType: PostingType,
  )
}
