package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.config.TAG_PRISON_REPORTS
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.reports.SummaryOfPaymentAndReceiptsReport
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.services.reports.ReportService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate

@Tag(name = TAG_PRISON_REPORTS)
@RestController
class PrisonReportsController(
  @param:Autowired private val reportService: ReportService,
) {

  @Operation(
    summary = "Get a summary of payments and receipts for a specific prison",
  )
  @GetMapping(
    path = [
      "/prisons/{prisonId}/reports/summary-of-payments-and-receipts",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK", content = [Content(schema = Schema(implementation = SummaryOfPaymentAndReceiptsReport::class))]),
      ApiResponse(
        responseCode = "404",
        description = "Prison not found",
        content = [
          Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE_SYNC')")
  fun getSummaryOfAccount(
    @PathVariable prisonId: String,
    @RequestParam date: LocalDate,
  ): ResponseEntity<SummaryOfPaymentAndReceiptsReport> {
    val postings = reportService.generateDailyPrisonSummaryReport(prisonId, date)

    val body = SummaryOfPaymentAndReceiptsReport(postings)
    return ResponseEntity.ok(body)
  }
}
