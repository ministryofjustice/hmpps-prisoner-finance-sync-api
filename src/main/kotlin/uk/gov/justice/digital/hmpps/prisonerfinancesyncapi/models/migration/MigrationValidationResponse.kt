package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GeneralLedgerDiscrepancyDetails

class MigrationValidationResponse(val validated: Boolean, val discrepancyDetails: List<GeneralLedgerDiscrepancyDetails>)
