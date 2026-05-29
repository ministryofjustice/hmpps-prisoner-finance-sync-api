package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.verify

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.TAG_NOMIS_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.verify.DailyReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import java.time.LocalDate

@Tag(name = TAG_NOMIS_SYNC)
@RestController
class VerifyController(
  private val generalLedgerService: GeneralLedgerService,
  private val timeConversionService: TimeConversionService,
) {

  @GetMapping(path = ["/verify/offender-transactions/{date}"])
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE_SYNC')")
  fun getDailyReconciliation(
    @PathVariable
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    date: LocalDate,
  ): ResponseEntity<DailyReconciliationResponse> {
    val startOfDay = timeConversionService.toUtcStartOfDay(date)

    val response = generalLedgerService.retrieveNomisGLTransactionsForDay(startOfDay)

    return ResponseEntity.ok(response)
  }
}
