package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
  name = "advance_repayment_mapping",
  uniqueConstraints = [
    UniqueConstraint(name = "uc_legacy_transaction_id_and_entry_sequence", columnNames = ["legacy_transaction_id", "entry_sequence"]),
  ],
)
data class AdvanceRepaymentMapping(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @Column(name = "legacy_transaction_id", nullable = false, unique = false)
  var legacyTransactionId: Long,

  @Column(name = "entry_sequence", nullable = false, unique = false)
  var entrySequence: Int,

  @Column(name = "general_ledger_transaction_uuid", nullable = false, unique = true)
  var generalLedgerTransactionUuid: UUID,
)
