package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

@Schema(description = "A request to synchronize all balances for a single prisoner across their sub-accounts.")
data class PrisonerBalancesSyncRequest(
  @field:Valid
  @field:NotEmpty
  @field:Schema(description = "A list of sub-account balances to be synchronized for the prisoner.")
  val accountBalances: List<PrisonerAccountPointInTimeBalance>,
)
