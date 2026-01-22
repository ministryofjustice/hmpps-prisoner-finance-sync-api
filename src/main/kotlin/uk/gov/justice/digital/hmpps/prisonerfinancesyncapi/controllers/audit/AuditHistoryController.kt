package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.audit

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Pattern
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.TAG_AUDIT
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_MESSAGE_PRISON_ID
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_REGEX_PRISON_ID
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadDetail
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadSummary
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.AuditHistoryService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate
import java.util.UUID

@Tag(name = TAG_AUDIT)
@RestController
@Validated
class AuditHistoryController(
  @param:Autowired private val auditHistoryService: AuditHistoryService,
) {
  @Operation(
    summary = "Get Audit History",
    description = "Get a list of synced payloads, including NOMIS' legacy IDs",
  )
  @GetMapping(
    path = ["/audit/history"],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Returns list of captured sync payloads.",
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
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO])
  @PreAuthorize("hasAnyAuthority('${ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO}')")
  fun getPayloadsByCaseloadAndDateRange(
    @RequestParam(required = false)
    @Pattern(
      regexp = VALIDATION_REGEX_PRISON_ID,
      message = VALIDATION_MESSAGE_PRISON_ID,
    ) prisonId: String?,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
  ): ResponseEntity<Page<NomisSyncPayloadSummary>> {
    val items = auditHistoryService.getPayloadsByCaseloadAndDateRange(prisonId, startDate, endDate, page, size)
    return ResponseEntity.ok(items)
  }

  @Operation(
    summary = "Get Payloads Details",
    description = "Get the full payload details (including the JSON body) for a specific request ID.",
  )
  @GetMapping(
    path = ["/audit/history/{requestId}"],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Returns the Payload matching the requestId provided.",
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
        responseCode = "404",
        description = "Not Found - No Payload found with requestId provided",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO])
  @PreAuthorize("hasAnyAuthority('${ROLE_PRISONER_FINANCE_SYNC__AUDIT__RO}')")
  fun getPayloadByRequestId(
    @PathVariable
    requestId: UUID,
  ): ResponseEntity<NomisSyncPayloadDetail> {
    val payload =
      auditHistoryService.getPayloadBodyByRequestId(requestId) ?: return ResponseEntity.notFound().build()

    return ResponseEntity.ok(payload)
  }
}
