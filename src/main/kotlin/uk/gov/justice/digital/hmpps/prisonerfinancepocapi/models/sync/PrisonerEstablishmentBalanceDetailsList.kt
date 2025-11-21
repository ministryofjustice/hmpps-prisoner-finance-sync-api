package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A list of a prisoner's sub account balances, broken down by establishment.")
data class PrisonerEstablishmentBalanceDetailsList(
  val items: List<PrisonerEstablishmentBalanceDetails>,
)
