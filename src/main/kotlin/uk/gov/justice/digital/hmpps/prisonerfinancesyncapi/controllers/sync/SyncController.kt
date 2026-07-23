package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.sync

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.TAG_NOMIS_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerService.SyncOffenderTransactionToGeneralLedgerResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync.SyncPayloadCaptureService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.util.UUID

@Tag(name = TAG_NOMIS_SYNC)
@RestController
class SyncController(
  private val generalLedgerService: GeneralLedgerService,
  private val syncPayloadCaptureService: SyncPayloadCaptureService,
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
        description = "Offender transactions successfully created.",
        content = [Content(schema = Schema(implementation = SyncTransactionReceipt::class))],
      ),
      ApiResponse(
        responseCode = "200",
        description = "All transactions previously posted successfully were created",
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
        responseCode = "422",
        description = "Partially processed - some transactions were successfully created but some failed.",
        content = [Content(schema = Schema(implementation = SyncTransactionReceipt::class))],
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
    if (request.offenderTransactions.isEmpty()) {
      throw CustomException(
        message = "Offender transactions missing",
        status = HttpStatus.BAD_REQUEST,
      )
    }

    val syncId = UUID.randomUUID()
    syncPayloadCaptureService.captureAndStoreRequest(
      request,
      syncId,
    )

    val result = generalLedgerService.syncOffenderTransaction(request)
    val receiptAction = getTransactionReceiptAction(result)

    val receipt = SyncTransactionReceipt(
      action = receiptAction,
      requestId = request.requestId,
      synchronizedTransactionId = syncId,
    )

    return when (receiptAction) {
      SyncTransactionReceipt.Action.CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(receipt)
      SyncTransactionReceipt.Action.PROCESSED -> ResponseEntity.ok(receipt)
      SyncTransactionReceipt.Action.PROCESSED_WITH_ERRORS -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(receipt)
      else -> throw IllegalStateException("Unexpected action: $receiptAction")
    }
  }

  private fun getTransactionReceiptAction(result: SyncOffenderTransactionToGeneralLedgerResponse): SyncTransactionReceipt.Action {
    if (
      result.previouslyMappedTransactionEntries.count() == 0 &&
      result.unsuccessfullyMappedTransactionEntries.count() == 0 &&
      result.successfullyMappedTransactionEntries.count() > 0
    ) {
      return SyncTransactionReceipt.Action.CREATED
    }

    if (
      result.unsuccessfullyMappedTransactionEntries.count() > 0
    ) {
      return SyncTransactionReceipt.Action.PROCESSED_WITH_ERRORS
    }

    return SyncTransactionReceipt.Action.PROCESSED
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
        responseCode = "501",
        description = "Method not implemented - General ledger transactions are not currently supported.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE_SYNC')")
  fun postGeneralLedgerTransaction(@Valid @RequestBody request: SyncGeneralLedgerTransactionRequest): ResponseEntity<SyncTransactionReceipt> = ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
}
