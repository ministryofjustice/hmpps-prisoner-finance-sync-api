package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "holds_mapping", indexes = [Index(name = "idx_holds_mapping_legacy_hold_number", columnList = "legacy_hold_number")])
data class HoldsMapping (

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @Column(name = "legacy_hold_number", nullable = false, unique = true)
  var legacyHoldNumber: Long,

  @Column(name = "holds_uuid", nullable = false, unique = true)
  var holdsUuid: UUID,

)