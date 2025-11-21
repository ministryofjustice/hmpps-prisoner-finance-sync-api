package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.sync

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.TAG_NOMIS_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.SyncQueryService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.SyncService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate
import java.util.UUID

@Tag(name = TAG_NOMIS_SYNC)
@RestController
class SyncController(
  @param:Autowired private val syncService: SyncService,
  @param:Autowired private val syncQueryService: SyncQueryService,
) {

  @Operation(
    summary = "Synchronize offender transactions",
    description = """
      Transactions that have not been posted before will be created.
      Those that have already been posted and can be identified will be updated with metadata only.
      If the core details of a transaction have changed, the ledger will need to reverse the original transaction and post a new one.
    """,
  )
  @PostMapping(
    path = ["/sync/offender-transactions"],
    consumes = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Offender transaction successfully created.",
        content = [Content(schema = Schema(implementation = SyncTransactionReceipt::class))],
      ),
      ApiResponse(
        responseCode = "200",
        description = "Offender transaction metadata successfully updated or processed with no new creations.",
        content = [Content(schema = Schema(implementation = SyncTransactionReceipt::class))],
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
  fun postOffenderTransaction(@Valid @RequestBody request: SyncOffenderTransactionRequest): ResponseEntity<SyncTransactionReceipt> {
    val receipt = syncService.syncTransaction(request)
    return when (receipt.action) {
      SyncTransactionReceipt.Action.CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(receipt)
      SyncTransactionReceipt.Action.UPDATED -> ResponseEntity.ok(receipt)
      SyncTransactionReceipt.Action.PROCESSED -> ResponseEntity.ok(receipt)
    }
  }

  @Operation(
    summary = "Synchronize general ledger transactions",
    description = """
      General ledger transactions that have not been posted before will be created.
      Those that have already been posted and can be identified will be updated with metadata only.
      If the core details of a transaction have changed, the ledger will need to reverse the original transaction and post a new one.
    """,
  )
  @PostMapping(
    path = ["/sync/general-ledger-transactions"],
    consumes = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "General ledger transaction successfully posted.",
        content = [Content(schema = Schema(implementation = SyncTransactionReceipt::class))],
      ),
      ApiResponse(
        responseCode = "200",
        description = "General ledger transaction metadata successfully updated or processed with no new creations.",
        content = [Content(schema = Schema(implementation = SyncTransactionReceipt::class))],
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
  fun postGeneralLedgerTransaction(@Valid @RequestBody request: SyncGeneralLedgerTransactionRequest): ResponseEntity<SyncTransactionReceipt> {
    val receipt = syncService.syncTransaction(request)

    return when (receipt.action) {
      SyncTransactionReceipt.Action.CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(receipt)
      SyncTransactionReceipt.Action.UPDATED -> ResponseEntity.ok(receipt)
      SyncTransactionReceipt.Action.PROCESSED -> ResponseEntity.ok(receipt)
    }
  }

  @Operation(
    summary = "Retrieve general ledger transactions by date range",
    description = "Fetches a list of general ledger transactions within a specified start and end date.",
  )
  @GetMapping(path = ["/sync/general-ledger-transactions"])
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "General ledger transactions successfully retrieved.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = SyncGeneralLedgerTransactionListResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad request - invalid date format or range.",
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
  fun getGeneralLedgerTransactionsByDate(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
  ): ResponseEntity<SyncGeneralLedgerTransactionListResponse> {
    val transactionsPage = syncQueryService.getGeneralLedgerTransactionsByDate(startDate, endDate, page, size)

    val response = SyncGeneralLedgerTransactionListResponse(
      transactions = transactionsPage.content,
      page = transactionsPage.number,
      totalElements = transactionsPage.totalElements,
      totalPages = transactionsPage.totalPages,
      last = transactionsPage.isLast,
    )

    return ResponseEntity.ok(response)
  }

  @Operation(
    summary = "Retrieve offender transactions by date range",
    description = "Fetches a list of offender transactions within a specified start and end date.",
  )
  @GetMapping(path = ["/sync/offender-transactions"])
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Offender transactions successfully retrieved.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = SyncOffenderTransactionListResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad request - invalid date format or range.",
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
  fun getOffenderTransactionsByDate(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
  ): ResponseEntity<SyncOffenderTransactionListResponse> {
    val transactionsPage = syncQueryService.getOffenderTransactionsByDate(startDate, endDate, page, size)

    val response = SyncOffenderTransactionListResponse(
      offenderTransactions = transactionsPage.content,
      page = transactionsPage.number,
      totalElements = transactionsPage.totalElements,
      totalPages = transactionsPage.totalPages,
      last = transactionsPage.isLast,
    )
    return ResponseEntity.ok(response)
  }

  @Operation(
    summary = "Retrieve a single general ledger transaction",
    description = "Fetches a single general ledger transaction by its ID.",
  )
  @GetMapping(path = ["/sync/general-ledger-transactions/{id}"])
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "General ledger transaction successfully retrieved.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = SyncGeneralLedgerTransactionResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Not Found - The specified transaction was not found.",
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
  fun getGeneralLedgerTransactionById(@PathVariable id: UUID): ResponseEntity<Any> {
    val transaction = syncQueryService.getGeneralLedgerTransactionById(id)
    return if (transaction != null) {
      ResponseEntity.ok(transaction)
    } else {
      ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(status = 404, developerMessage = "Transaction with ID $id not found."))
    }
  }

  @Operation(
    summary = "Retrieve an offender transaction by its ID",
    description = "Fetches a single offender transaction by its ID.",
  )
  @GetMapping(path = ["/sync/offender-transactions/{id}"])
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Offender transaction successfully retrieved.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = SyncOffenderTransactionResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Not Found - The specified transaction ID was not found.",
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
  fun getOffenderTransactionById(
    @PathVariable id: UUID,
  ): ResponseEntity<Any> {
    val transaction = syncQueryService.getOffenderTransactionById(id)
    return if (transaction != null) {
      ResponseEntity.ok(transaction)
    } else {
      ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(status = 404, developerMessage = "Transaction with ID $id not found."))
    }
  }
}
