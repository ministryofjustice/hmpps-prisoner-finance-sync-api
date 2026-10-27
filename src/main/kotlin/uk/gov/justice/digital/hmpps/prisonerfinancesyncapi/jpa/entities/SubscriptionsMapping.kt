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
  name = "subscriptions_mapping",
  uniqueConstraints = [
    UniqueConstraint(
      name = "uc_subscriptions_mapping_legacy_payment_profile_id",
      columnNames = ["legacy_payment_profile_id"],
    ),
    UniqueConstraint(
      name = "uc_subscriptions_mapping_subscription_uuid",
      columnNames = ["subscription_uuid"],
    ),
  ],
)
data class SubscriptionsMapping(

  @Id
  @Column(name = "subscription_mapping_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var subscriptionMappingId: Long? = null,

  @Column(name = "legacy_payment_profile_id", nullable = false, unique = true)
  var legacyPaymentProfileId: Long,

  @Column(name = "subscription_uuid", nullable = false, unique = true)
  var subscriptionUuid: UUID,

)
