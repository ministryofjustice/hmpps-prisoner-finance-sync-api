package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class GlTransactionRequest(
  val timestamp: Instant,
  val amount: BigDecimal,
  val creditorAccount: UUID,
  val debtorAccount: UUID,
  val reference: String,
  val description: String?,
)

data class GlSubAccountRequest(
  val name: String,
  val reference: String,
)

data class GlSubAccountResponse(
  val id: UUID,
  val parentAccountId: UUID,
  val reference: String,
)

data class GlTransactionReceipt(
  val id: UUID,
)

data class GlAccountRequest(
  val name: String,
  val reference: String,
)

data class GlAccountResponse(
  val id: UUID,
  val name: String,
  val references: List<String>,
)
