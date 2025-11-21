package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "transaction")
data class Transaction(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(name = "uuid", nullable = false)
  val uuid: UUID = UUID.randomUUID(),

  @Column(name = "transaction_type", nullable = false)
  val transactionType: String,

  @Column(name = "description", nullable = false)
  val description: String,

  @Column(name = "date", nullable = false)
  val date: Timestamp,

  @Column(name = "legacy_transaction_id")
  val legacyTransactionId: Long? = null,

  @Column(name = "synchronized_transaction_id")
  val synchronizedTransactionId: UUID? = null,

  @Column(name = "prison")
  val prison: String? = null,

  @Column(name = "created_at")
  val createdAt: Instant? = null,
)
