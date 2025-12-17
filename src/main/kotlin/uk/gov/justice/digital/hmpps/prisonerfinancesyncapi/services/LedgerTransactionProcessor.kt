package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.util.UUID

interface LedgerTransactionProcessor {
  fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): UUID
  fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID
}
