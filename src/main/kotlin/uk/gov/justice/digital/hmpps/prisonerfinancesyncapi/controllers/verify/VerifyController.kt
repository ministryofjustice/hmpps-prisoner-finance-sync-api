package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.verify

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.TAG_NOMIS_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.verify.DailyReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate

@Tag(name = TAG_NOMIS_SYNC)
@RestController
class VerifyController(
  private val generalLedgerService: GeneralLedgerService,
  private val timeConversionService: TimeConversionService,
) {

  @Operation(
    summary = "Verify NOMIS transactions in the General Ledger",
    description = "Retrieve a list of NOMIS transactions synchronised to the Prisoner Finance general ledger on a given date, using the createdAt field.",
  )
  @GetMapping(path = ["/verify/offender-transactions/{date}"])
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "General ledger transactions successfully retrieved.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = DailyReconciliationResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad request - invalid date format.",
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
  fun getDailyReconciliation(
    @Parameter(description = "The date and time the transaction was created", example = "2020-03-28")
    @PathVariable
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    date: LocalDate,
  ): ResponseEntity<DailyReconciliationResponse> {
    val startOfDay = timeConversionService.toUtcStartOfDay(date)

    val response = generalLedgerService.retrieveNomisGLTransactionsForDay(startOfDay)

    return ResponseEntity.ok(response)
  }
}
