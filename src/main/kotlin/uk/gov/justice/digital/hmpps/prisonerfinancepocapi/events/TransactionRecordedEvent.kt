package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.events

import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities.TransactionEntry

data class TransactionRecordedEvent(
  val savedEntries: List<TransactionEntry>,
)
