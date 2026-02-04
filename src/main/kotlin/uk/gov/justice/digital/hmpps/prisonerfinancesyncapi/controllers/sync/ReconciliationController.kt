package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.sync

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
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.TAG_NOMIS_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.DualWriteLedgerService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService

@Tag(name = TAG_NOMIS_SYNC)
@RestController
class ReconciliationController(
  @param:Autowired private val ledgerQueryService: LedgerQueryService,
  @param:Autowired private val dualWriteLedgerService: DualWriteLedgerService,
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
    val body = dualWriteLedgerService.reconcilePrisoner(prisonNumber)
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
}
