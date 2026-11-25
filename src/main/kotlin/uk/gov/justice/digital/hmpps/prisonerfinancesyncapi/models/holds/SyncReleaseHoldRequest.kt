package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class SyncReleaseHoldRequest(
  @field:Schema(description = "The time the hold was released according to NOMIS")
  val releaseDateTime: LocalDateTime,

  @field:Schema(description = "The legacy transaction Id of the release transaction")
  val releaseTransactionId: Long,

  @field:Schema(description = "The username who made the release")
  val updatedBy: String? = null,
)
