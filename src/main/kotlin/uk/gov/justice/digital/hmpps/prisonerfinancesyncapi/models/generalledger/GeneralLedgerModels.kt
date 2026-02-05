package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingType.CR
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingType.DR
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

data class GlTransactionRequest(
  val reference: String,
  val description: String,
  val timestamp: Instant,
  val amount: Long,
  val postings: List<GlPostingRequest>,
)

data class GlPostingRequest(
  val subAccountId: UUID,
  val type: PostingType,
  val amount: Long,
)

enum class PostingType {
  DR,
  CR,
}

fun String.toGLPostingType() = when (this) {
  "DR" -> DR
  "CR" -> CR
  else ->
    throw IllegalArgumentException("Invalid posting type $this")
}

data class GlSubAccountRequest(
  val subAccountReference: String,
)

data class GlSubAccountResponse(
  val id: UUID,
  val parentAccountId: UUID,
  val reference: String,
  val createdAt: LocalDateTime,
  val createdBy: String,
)

data class GlTransactionReceipt(
  val id: UUID,
  val reference: String,
  val amount: Long,
)

data class GlAccountRequest(
  val accountReference: String,
)

data class GlAccountResponse(
  val id: UUID,
  val reference: String,
  val createdAt: LocalDateTime,
  val createdBy: String,
  val subAccounts: List<GlSubAccountResponse>? = emptyList(),
)

data class GlAccountBalanceResponse(
  val accountId: UUID,
  val balanceDateTime: LocalDateTime,
  val amount: Long,
)
