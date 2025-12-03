package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.domainevents

import com.google.gson.annotations.SerializedName

data class HmppsDomainEvent(
  val eventType: String,
  val additionalInformation: AdditionalInformation,
)

data class AdditionalInformation(
  val nomsNumber: String,
  val removedNomsNumber: String,
  val reason: String? = null,
)

data class Event(
  @SerializedName("Message")
  val message: String,
)
