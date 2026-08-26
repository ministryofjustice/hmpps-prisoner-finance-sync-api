package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.holds

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.HOLDS
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldRequest

@Tag(name = HOLDS)
@RestController
class HoldsController {
  @PostMapping("/sync/holds")
  fun postHolds(@RequestBody createHoldRequest: SyncCreateHoldRequest){

  }
}