package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.sync

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A list of general ledger account balances")
data class GeneralLedgerBalanceDetailsList(val items: List<GeneralLedgerBalanceDetails>)
