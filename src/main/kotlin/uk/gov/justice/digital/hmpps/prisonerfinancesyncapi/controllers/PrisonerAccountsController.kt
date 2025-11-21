package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.TAG_PRISONER_ACCOUNTS
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.PrisonerSubAccountDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.PrisonerSubAccountDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.TransactionDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Tag(name = TAG_PRISONER_ACCOUNTS)
@RestController
class PrisonerAccountsController(
  @param:Autowired private val ledgerQueryService: LedgerQueryService,
) {

  @Operation(
    summary = "Get the details of a specific account for a prisoner",
  )
  @GetMapping(
    path = [
      "/prisoners/{prisonNumber}/accounts/{accountCode}",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK", content = [Content(schema = Schema(implementation = PrisonerSubAccountDetails::class))]),
      ApiResponse(
        responseCode = "404",
        description = "Prisoner account not found",
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
  fun getPrisonerSubAccountDetails(
    @PathVariable prisonNumber: String,
    @PathVariable accountCode: Int,
  ): ResponseEntity<PrisonerSubAccountDetails> {
    val accountDetails = ledgerQueryService.getPrisonerSubAccountDetails(prisonNumber, accountCode)
      ?: throw NoResourceFoundException(
        HttpMethod.GET,
        "Prisoner account not found for offender: $prisonNumber and account code: $accountCode",
      )
    return ResponseEntity.ok(accountDetails)
  }

  @Operation(
    summary = "Get a list of all subaccounts for a specific prisoner",
  )
  @GetMapping(
    path = [
      "/prisoners/{prisonNumber}/accounts",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK", content = [Content(schema = Schema(implementation = PrisonerSubAccountDetailsList::class))]),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE_SYNC')")
  fun listPrisonerAccounts(
    @PathVariable prisonNumber: String,
  ): ResponseEntity<PrisonerSubAccountDetailsList> {
    val items = ledgerQueryService.listPrisonerSubAccountDetails(prisonNumber)
    val body = PrisonerSubAccountDetailsList(items)
    return ResponseEntity.ok(body)
  }

  @Operation(
    summary = "Get list of transactions for a specific sub account for a specific prisoner",
  )
  @GetMapping(
    path = [
      "/prisoners/{prisonNumber}/accounts/{accountCode}/transactions",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK", content = [Content(schema = Schema(implementation = TransactionDetailsList::class))]),
      ApiResponse(
        responseCode = "404",
        description = "Prisoner account not found",
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
  fun getPrisonerAccountTransactions(
    @PathVariable prisonNumber: String,
    @PathVariable accountCode: Int,
  ): ResponseEntity<TransactionDetailsList> {
    val items = ledgerQueryService.listPrisonerSubAccountTransactions(prisonNumber, accountCode)

    if (items.isEmpty()) {
      throw NoResourceFoundException(HttpMethod.GET, "Account not found for offender: $prisonNumber and account code: $accountCode")
    }

    val body = TransactionDetailsList(items)
    return ResponseEntity.ok(body)
  }

  @Operation(
    summary = "Get a transaction for a specific sub account for a specific prisoner",
  )
  @GetMapping(
    path = [
      "/prisoners/{prisonNumber}/accounts/{accountCode}/transactions/{transactionId}",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK", content = [Content(schema = Schema(implementation = TransactionDetailsList::class))]),
      ApiResponse(
        responseCode = "404",
        description = "Prisoner account not found",
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
  fun getPrisonerAccountTransaction(
    @PathVariable prisonNumber: String,
    @PathVariable accountCode: Int,
    @PathVariable transactionId: String,
  ): ResponseEntity<TransactionDetailsList> {
    val items = ledgerQueryService.getTransaction(prisonNumber, accountCode, transactionId)

    if (items.isEmpty()) {
      throw NoResourceFoundException(
        HttpMethod.GET,
        "Prisoner account transaction not found for offender: $prisonNumber, account code: $accountCode, and transaction ID: $transactionId",
      )
    }

    val body = TransactionDetailsList(items)
    return ResponseEntity.ok(body)
  }
}
