package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

interface NomisSyncPayloadDetails {
  val id: Long?
  val timestamp: Instant
  val legacyTransactionId: Long?
  val synchronizedTransactionId: UUID
  val requestId: UUID
  val caseloadId: String?
  val requestTypeIdentifier: String?
  val transactionTimestamp: Instant?
  val body: String
}

@Entity
@Table(name = "nomis_sync_payloads")
data class NomisSyncPayload(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  override var id: Long? = null,

  @Column(name = "timestamp", nullable = false)
  override val timestamp: Instant,

  @Column(name = "legacy_transaction_id")
  override val legacyTransactionId: Long?,

  @Column(name = "synchronized_transaction_id")
  override val synchronizedTransactionId: UUID,

  @Column(name = "request_id", unique = true)
  override val requestId: UUID,

  @Column(name = "caseload_id")
  override val caseloadId: String?,

  @Column(name = "request_type_identifier")
  override val requestTypeIdentifier: String?,

  @Column(name = "transaction_timestamp")
  override val transactionTimestamp: Instant? = null,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column
  override val body: String,
) : NomisSyncPayloadDetails
