package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.holds

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.HOLDS
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.ReleasedHoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncReleaseHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncReleasedHoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.HoldsService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Tag(name = HOLDS)
@RestController
class HoldsController(var holdsService: HoldsService) {

  @Operation(
    summary = "Create a new Hold record",
    description = "Creates a new Hold record for the specified prisoner in the holds service",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Hold record created.",
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
        responseCode = "409",
        description = "Conflict - Legacy Hold Number already in use",
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
  @PostMapping("/sync/holds")
  fun postHolds(@RequestBody createHoldRequest: SyncCreateHoldRequest): ResponseEntity<HoldResponse> {
    val createdHold = holdsService.createHold(syncCreateHoldRequest = createHoldRequest)
    return ResponseEntity.status(201).body(createdHold)
  }

  @Operation(
    summary = "Release an existing hold record",
    description = "Releases a specific hold in the holds service",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Hold record released.",
        content = [Content(schema = Schema(implementation = ReleasedHoldResponse::class))],
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
  @PostMapping("/sync/holds/{holdNumber}/release")
  fun releaseHold(@PathVariable holdNumber: Long, @RequestBody syncHoldReleaseRequest: SyncReleaseHoldRequest): ResponseEntity<SyncReleasedHoldResponse> {
    val response = holdsService.releaseHold(holdNumber, releaseRequest = syncHoldReleaseRequest)

    return ResponseEntity.status(200).body(response)
  }
}
