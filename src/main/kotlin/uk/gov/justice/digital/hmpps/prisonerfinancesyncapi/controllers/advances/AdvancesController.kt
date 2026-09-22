package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.advances

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ADVANCES
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.AdvancesService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Tag(name = ADVANCES)
@RestController
class AdvancesController(private val advancesService: AdvancesService) {

  @Operation(
    summary = "Create a new Advance record",
    description = "Creates a new Advance record for the specified prisoner in the advance service",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Advance record created.",
        content = [Content(schema = Schema(implementation = HoldResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad request - invalid input data.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized - requires a valid OAuth2 token",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden - requires an appropriate role",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE_SYNC')")
  @PostMapping("/sync/advances")
  fun postAdvance(@RequestBody createAdvanceRequest: SyncCreateAdvanceRecordRequest): ResponseEntity<SyncCreateAdvanceRecordResponse> {
    val syncCreateAdvanceRecordResponse = advancesService.createAdvance(createAdvanceRequest)
    return ResponseEntity.status(201).body(syncCreateAdvanceRecordResponse)
  }
}
