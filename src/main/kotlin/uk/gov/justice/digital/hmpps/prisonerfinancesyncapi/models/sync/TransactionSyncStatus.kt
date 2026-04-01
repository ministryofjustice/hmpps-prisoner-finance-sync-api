package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync

import java.util.UUID

sealed interface TransactionSyncStatus {
  data class Duplicate(val synchronizedTransactionId: UUID) : TransactionSyncStatus
  data class Updated(val synchronizedTransactionId: UUID) : TransactionSyncStatus
  object New : TransactionSyncStatus
}
