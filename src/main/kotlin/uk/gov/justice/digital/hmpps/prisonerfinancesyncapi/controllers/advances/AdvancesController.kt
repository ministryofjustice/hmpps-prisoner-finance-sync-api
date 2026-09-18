package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.advances

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ADVANCES
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.AdvancesService

@Tag(name = ADVANCES)
@RestController
class AdvancesController(private val advancesService: AdvancesService) {
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE_SYNC')")
  @PostMapping("/sync/advances")
  fun postAdvance(@RequestBody createAdvanceRequest: SyncCreateAdvanceRecordRequest) : ResponseEntity<AdvanceRecordResponse> {
    val createAdvanceRecordResponse = advancesService.createAdvance(createAdvanceRequest)
    return ResponseEntity.status(201).body(createAdvanceRecordResponse)
  }
}