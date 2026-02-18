package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
  name = "general_ledger_transaction_mapping",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["legacy_transaction_id", "entry_sequence"]),
  ],
)
class GeneralLedgerTransactionMapping(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false)
  val legacyTransactionId: Long,

  @Column(nullable = false)
  val entrySequence: Int,

  @Column(nullable = false)
  val glTransactionUuid: UUID,

  @Column(nullable = false)
  val createdAt: Instant = Instant.now(),
)
