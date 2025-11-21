package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A list of prisoner sub-accounts (e.g., Cash, Spends, Savings)")
data class PrisonerSubAccountDetailsList(val items: List<PrisonerSubAccountDetails>)
