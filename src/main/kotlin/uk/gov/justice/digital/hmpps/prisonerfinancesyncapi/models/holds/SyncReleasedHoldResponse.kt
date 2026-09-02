package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDateTime

data class SyncReleasedHoldResponse(

  @param:JsonProperty("prisonNumber")
  val prisonNumber: String,

  @param:JsonProperty("holdNumber")
  val holdNumber: Long,

  @param:JsonProperty("amountReleased")
  val amountReleased: BigDecimal,

  @param:JsonProperty("releasedAt")
  val releasedAt: LocalDateTime,
)
