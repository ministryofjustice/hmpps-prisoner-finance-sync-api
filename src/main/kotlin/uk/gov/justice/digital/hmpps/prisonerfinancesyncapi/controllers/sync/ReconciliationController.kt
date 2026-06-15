package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.sync

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Min
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.TAG_NOMIS_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PagedTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ReconciliationService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate

@Validated
@Tag(name = TAG_NOMIS_SYNC)
@RestController
class ReconciliationController(
  @param:Autowired private val ledgerQueryService: LedgerQueryService,
  @param:Autowired private val reconciliationService: ReconciliationService,
  @param:Autowired private val generalLedgerService: GeneralLedgerService,
) {
  @Operation(
    summary = "Get a list of all subaccount balances for a prisoner, grouped by establishment where transactions occurred",
  )
  @GetMapping(
    path = [
      "/reconcile/prisoner-balances/{prisonNumber}",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK", content = [Content(schema = Schema(implementation = PrisonerEstablishmentBalanceDetailsList::class))]),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE_SYNC')")
  fun listPrisonerBalancesByEstablishment(
    @PathVariable prisonNumber: String,
  ): ResponseEntity<PrisonerEstablishmentBalanceDetailsList> {
    val body = reconciliationService.reconcilePrisoner(prisonNumber)
    return ResponseEntity.ok(body)
  }

  @Operation(
    summary = "Get a list of all general ledger balances accounts for a specific prison",
  )
  @GetMapping(
    path = [
      "/reconcile/general-ledger-balances/{prisonId}",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK", content = [Content(schema = Schema(implementation = GeneralLedgerBalanceDetailsList::class))]),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE_SYNC')")
  fun listGeneralLedgerBalances(
    @PathVariable prisonId: String,
  ): ResponseEntity<GeneralLedgerBalanceDetailsList> {
    val items = ledgerQueryService.listGeneralLedgerBalances(prisonId)
    val body = GeneralLedgerBalanceDetailsList(items)
    return ResponseEntity.ok(body)
  }

  @Operation(
    summary = "Retrieve an offender transaction by its ID using data from the prisoner general ledger",
  )
  @GetMapping(
    path = [
      "/reconcile/offender-transactions/{legacyTransactionId}",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieve an offender transaction by its ID using data from the prisoner general ledger",
        content = [Content(schema = Schema(implementation = SyncGeneralLedgerTransactionResponse::class))],
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
        description = "Not Found - Prison Number not found",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "502",
        description = "General Ledger threw an unexpected 5XX error.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE_SYNC')")
  fun getTransactionReconciliationById(@PathVariable legacyTransactionId: Long): ResponseEntity<SyncGeneralLedgerTransactionResponse> {
    val response = generalLedgerService.retrieveNomisGLTransactionByLegacyTransactionId(legacyTransactionId)

    return ResponseEntity.ok(response)
  }

  @Operation(
    summary = "Retrieve paginated list of offender transactions by a date range using data from the prisoner general ledger",
  )
  @GetMapping(
    path = [
      "/reconcile/offender-transactions",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieve offender transactions by a date range using data from the prisoner general ledger",
        content = [Content(schema = Schema(implementation = SyncGeneralLedgerTransactionResponse::class))],
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
      ApiResponse(
        responseCode = "502",
        description = "General Ledger threw an unexpected 5XX error.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE_SYNC')")
  fun getTransactionReconciliationByDateRange(
    @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
    @RequestParam(required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
    @RequestParam(defaultValue = "1") @Min(1) pageNumber: Int,
    @RequestParam(defaultValue = "25") @Min(1) pageSize: Int,
  ): ResponseEntity<PagedTransactionResponse> {
    if (startDate.isAfter(endDate)) {
      throw CustomException(message = "startDate cannot be after endDate", status = HttpStatus.BAD_REQUEST)
    }

    val response = generalLedgerService.retrieveNomisGLTransactionByDateRange(
      startDate = startDate,
      endDate = endDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
    return ResponseEntity.ok(response)
  }
}
