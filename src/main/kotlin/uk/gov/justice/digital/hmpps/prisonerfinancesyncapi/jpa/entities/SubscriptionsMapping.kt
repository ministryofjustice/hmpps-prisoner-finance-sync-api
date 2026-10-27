package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(
  name = "subscriptions_mapping",
  indexes = [
    Index(
      name = "idx_subscriptions_mapping_legacy_payment_profile_id",
      columnList = "legacy_payment_profile_id",
    ),
  ],
)
data class SubscriptionsMapping(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @Column(name = "legacy_payment_profile_id", nullable = false, unique = true)
  var legacyPaymentProfileId: Long,

  @Column(name = "subscription_uuid", nullable = false, unique = true)
  var subscriptionUuid: UUID,

)
