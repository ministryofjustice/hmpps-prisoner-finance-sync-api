package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.controllers.sync

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.config.TAG_NOMIS_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.services.migration.MigrationService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Tag(name = TAG_NOMIS_SYNC)
@RestController
class MigrationController(@param:Autowired private val migrationService: MigrationService) {

  @Operation(
    summary = "Migrate general ledger balances for a single prison",
    description = "Migrate general ledger account balances for a specific prison.",
  )
  @PostMapping(
    path = ["/migrate/general-ledger-balances/{prisonId}"],
    consumes = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "General ledger balances successfully migrated.",
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
  @PreAuthorize("hasAnyAuthority('${ROLE_PRISONER_FINANCE_SYNC}')")
  fun migrateGeneralLedgerBalances(
    @PathVariable prisonId: String,
    @RequestBody @Valid request: GeneralLedgerBalancesSyncRequest,
  ): ResponseEntity<Void> {
    migrationService.migrateGeneralLedgerBalances(prisonId, request)
    return ResponseEntity.ok().build()
  }

  @Operation(
    summary = "Migrate a prisoners balances",
    description = "Migrate a prisoners sub-accounts (e.g., Spends, Private Cash) to a given balance from a legacy system.",
  )
  @PostMapping(
    path = ["/migrate/prisoner-balances/{prisonNumber}"],
    consumes = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner balances successfully migrated.",
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
  @PreAuthorize("hasAnyAuthority('${ROLE_PRISONER_FINANCE_SYNC}')")
  fun migratePrisonerBalances(
    @PathVariable prisonNumber: String,
    @RequestBody @Valid request: PrisonerBalancesSyncRequest,
  ): ResponseEntity<Void> {
    migrationService.migratePrisonerBalances(prisonNumber, request)
    return ResponseEntity.ok().build()
  }
}
