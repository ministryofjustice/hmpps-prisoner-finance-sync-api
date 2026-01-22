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

@Entity
@Table(name = "nomis_sync_payloads")
data class NomisSyncPayload(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @Column(name = "timestamp", nullable = false)
  val timestamp: Instant,

  @Column(name = "legacy_transaction_id")
  val legacyTransactionId: Long?,

  @Column(name = "synchronized_transaction_id")
  val synchronizedTransactionId: UUID,

  @Column(name = "request_id", unique = true)
  val requestId: UUID,

  @Column(name = "caseload_id")
  val caseloadId: String?,

  @Column(name = "request_type_identifier")
  val requestTypeIdentifier: String?,

  @Column(name = "transaction_timestamp")
  val transactionTimestamp: Instant? = null,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column
  val body: String,
)
