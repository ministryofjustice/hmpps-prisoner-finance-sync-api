package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A list of accounts for a specific prison")
data class PrisonAccountDetailsList(val items: List<PrisonAccountDetails>)
