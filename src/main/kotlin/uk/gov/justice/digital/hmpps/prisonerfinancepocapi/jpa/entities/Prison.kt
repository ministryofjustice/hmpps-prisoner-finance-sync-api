package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "prison")
data class Prison(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(name = "uuid", nullable = false)
  val uuid: UUID = UUID.randomUUID(),

  @Column(name = "code", nullable = false)
  val code: String,
)
