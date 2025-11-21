package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

@Schema(description = "A request to synchronize general ledger balances for a single prison.")
data class GeneralLedgerBalancesSyncRequest(
  @field:Valid
  @field:NotEmpty
  @field:Schema(description = "A list of general ledger account balances to be synchronized.")
  val accountBalances: List<GeneralLedgerPointInTimeBalance>,
)
