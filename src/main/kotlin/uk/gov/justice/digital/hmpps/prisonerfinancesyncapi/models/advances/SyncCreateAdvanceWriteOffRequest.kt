package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class SyncCreateAdvanceWriteOffRequest(

  @field:Schema(description = "The date and time of the written off advance", example = "2024-06-18T00:00:00.000000", required = true)
  val writeOffDateTime: LocalDateTime,

  @field:Schema(description = "The username that wrote off this advance", example = "JOHN_USER", required = true)
  val writtenOffBy: String,

)
