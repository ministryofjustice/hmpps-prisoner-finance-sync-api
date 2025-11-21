package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.events

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.TransactionEntry

data class TransactionRecordedEvent(
  val savedEntries: List<TransactionEntry>,
)
