package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.advances

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ADVANCES
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.AdvancesService

@Tag(name = ADVANCES)
@RestController
class AdvancesController(private val advancesService: AdvancesService) {
  @PostMapping("/sync/advances")
  fun postAdvance(@RequestBody createAdvanceRequest: SyncCreateAdvanceRecordRequest) : ResponseEntity<AdvanceRecordResponse> {
    val createAdvanceRecordResponse = advancesService.createAdvance(createAdvanceRequest)
    return ResponseEntity.status(201).body(createAdvanceRecordResponse)
  }
}