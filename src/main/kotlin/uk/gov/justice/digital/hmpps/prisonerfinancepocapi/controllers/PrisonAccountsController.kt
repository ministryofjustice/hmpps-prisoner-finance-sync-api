package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.config.TAG_PRISON_ACCOUNTS
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.PrisonAccountDetails
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.PrisonAccountDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models.TransactionDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.services.ledger.LedgerQueryService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate

@Tag(name = TAG_PRISON_ACCOUNTS)
@RestController
class PrisonAccountsController(
  @param:Autowired private val ledgerQueryService: LedgerQueryService,

) {

  @Operation(
    summary = "Get the details of a specific prison account",
  )
  @GetMapping(
    path = [
      "/prisons/{prisonId}/accounts/{accountCode}",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK", content = [Content(schema = Schema(implementation = PrisonAccountDetails::class))]),
      ApiResponse(
        responseCode = "404",
        description = "Prison account not found",
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
  fun getPrisonAccountDetails(
    @PathVariable prisonId: String,
    @PathVariable accountCode: Int,
  ): ResponseEntity<PrisonAccountDetails> {
    val accountDetails = ledgerQueryService.getPrisonAccountDetails(prisonId, accountCode)
      ?: throw NoResourceFoundException(HttpMethod.GET, "Prison account not found for code: $prisonId and account code: $accountCode")

    return ResponseEntity.ok(accountDetails)
  }

  @Operation(
    summary = "Get a list of all accounts for a specific prison",
  )
  @GetMapping(
    path = [
      "/prisons/{prisonId}/accounts",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK", content = [Content(schema = Schema(implementation = PrisonAccountDetailsList::class))]),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE_SYNC')")
  fun listPrisonAccounts(
    @PathVariable prisonId: String,
  ): ResponseEntity<PrisonAccountDetailsList> {
    val items = ledgerQueryService.listPrisonAccountDetails(prisonId)
    val body = PrisonAccountDetailsList(items)
    return ResponseEntity.ok(body)
  }

  @Operation(
    summary = "Get list of transactions for a specific account for a specific prison",
  )
  @GetMapping(
    path = [
      "/prisons/{prisonId}/accounts/{accountCode}/transactions",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK", content = [Content(schema = Schema(implementation = TransactionDetailsList::class))]),
      ApiResponse(
        responseCode = "404",
        description = "Prison account not found",
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
  fun getPrisonAccountTransactions(
    @PathVariable prisonId: String,
    @PathVariable accountCode: Int,
    @Parameter(description = "Optional filter by a specific business day", example = "2023-01-25")
    date: LocalDate? = null,
  ): ResponseEntity<TransactionDetailsList> {
    val items = ledgerQueryService.listPrisonAccountTransactions(prisonId, accountCode, date)

    if (items.isEmpty()) {
      throw NoResourceFoundException(HttpMethod.GET, "Prison account not found for code: $prisonId and account code: $accountCode")
    }

    val body = TransactionDetailsList(items)
    return ResponseEntity.ok(body)
  }

  @Operation(
    summary = "Get a transaction for a specific account for a specific prison",
  )
  @GetMapping(
    path = [
      "/prisons/{prisonId}/accounts/{accountCode}/transactions/{transactionId}",
    ],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "OK", content = [Content(schema = Schema(implementation = TransactionDetailsList::class))]),
      ApiResponse(
        responseCode = "404",
        description = "Prison account not found",
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
  fun getPrisonAccountTransaction(
    @PathVariable prisonId: String,
    @PathVariable accountCode: Int,
    @PathVariable transactionId: String,
  ): ResponseEntity<TransactionDetailsList> {
    val items = ledgerQueryService.getPrisonAccountTransaction(prisonId, accountCode, transactionId)

    if (items.isEmpty()) {
      throw NoResourceFoundException(
        HttpMethod.GET,
        "Prison account transaction not found for prison: $prisonId, account code: $accountCode, and transaction ID: $transactionId",
      )
    }

    val body = TransactionDetailsList(items)
    return ResponseEntity.ok(body)
  }
}
