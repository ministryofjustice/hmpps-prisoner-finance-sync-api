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
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.TAG_AUDIT
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_MESSAGE_PRISON_ID
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_REGEX_PRISON_ID
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.PayloadTransactionDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.AuditHistoryService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.Instant

@Tag(name = TAG_AUDIT)
@RestController
@Validated
class HistoryController(
  @param:Autowired private val auditHistoryService: AuditHistoryService,
) {
  @Operation(
    summary = "Get Payloads",
    description = "Get a list of synced payloads, including Nomis' legacy IDs",
  )
  @GetMapping(
    path = ["/audit/history"],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Returns all offender transactions for the specified prisoner.",
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
  fun getPayloadsByCaseloadAndDateRange(
    @RequestParam
    @Pattern(
      regexp = VALIDATION_REGEX_PRISON_ID,
      message = VALIDATION_MESSAGE_PRISON_ID,
    ) prisonId: String,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: Instant?,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: Instant?,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
  ): ResponseEntity<PayloadTransactionDetailsList> {
    val items = auditHistoryService.getPayloadsByCaseloadAndDateRange(prisonId, startDate, endDate, page, size)
    val body = PayloadTransactionDetailsList(items)
    return ResponseEntity.ok(body)
  }
}
