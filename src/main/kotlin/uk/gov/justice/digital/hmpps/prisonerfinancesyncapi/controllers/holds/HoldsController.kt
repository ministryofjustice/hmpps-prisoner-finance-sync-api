package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.holds

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.HOLDS
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.HoldsService

@Tag(name = HOLDS)
@RestController
class HoldsController(var holdsService: HoldsService) {
  @PostMapping("/sync/holds")
  fun postHolds(@RequestBody createHoldRequest: SyncCreateHoldRequest) : ResponseEntity<HoldResponse>{
    val createdHold = holdsService.createHold(syncCreateHoldRequest = createHoldRequest)
    return ResponseEntity.status(201).body(createdHold)
  }
}