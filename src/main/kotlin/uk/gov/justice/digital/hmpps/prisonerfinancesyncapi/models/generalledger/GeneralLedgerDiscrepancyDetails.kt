package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetails

data class GeneralLedgerDiscrepancyDetails(
  val message: String,
  val prisonerId: String,
  val accountType: String,
  val legacyAggregatedBalance: Long,
  val generalLedgerBalance: Long,
  val discrepancy: Long,
  val glBreakdown: List<GlSubAccountBalanceResponse>,
  val legacyBreakdown: List<PrisonerEstablishmentBalanceDetails>,
)
