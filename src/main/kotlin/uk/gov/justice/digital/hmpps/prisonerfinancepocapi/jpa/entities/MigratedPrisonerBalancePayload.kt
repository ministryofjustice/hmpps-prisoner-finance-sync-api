package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "migrated_prisoner_balances")
data class MigratedPrisonerBalancePayload(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @Column(name = "prison_number", nullable = false)
  val prisonNumber: String,

  @Column(name = "timestamp", nullable = false)
  val timestamp: Instant,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  val body: String,
)
