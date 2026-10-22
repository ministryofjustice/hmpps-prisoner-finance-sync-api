package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.subscriptions

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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.TAG_SUBSCRIPTIONS
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncCreateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncCreateSubscriptionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncUpdateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncUpdateSubscriptionResponse
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate
import java.util.UUID

@Tag(name = TAG_SUBSCRIPTIONS)
@RestController
class SubscriptionController {

  @Operation(
    summary = "Create a new subscription record for a prisoner",
    description = "Create a new subscription record for a prisoner",
  )
  @PostMapping(
    path = ["/sync/subscriptions"],
    consumes = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Subscription record created",
        content = [Content(schema = Schema(implementation = SyncCreateSubscriptionResponse::class))],
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
        responseCode = "501",
        description = "This endpoint is not yet implemented.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('${ROLE_PRISONER_FINANCE_SYNC}')")
  fun createSubscription(
    @Suppress("UNUSED_PARAMETER")
    @RequestBody
    @Valid request: SyncCreateSubscriptionRequest,
  ): ResponseEntity<SyncCreateSubscriptionResponse> = ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(
    SyncCreateSubscriptionResponse(
      subscriptionId = UUID.randomUUID(),
    ),
  )

  @Operation(
    summary = "Update a subscription record for a prisoner",
    description = "Update a subscription record for a prisoner",
  )
  @PostMapping(
    path = ["/sync/subscriptions/{subscriptionId}/update"],
    consumes = [MediaType.APPLICATION_JSON_VALUE],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Subscription record updated",
        content = [Content(schema = Schema(implementation = SyncUpdateSubscriptionResponse::class))],
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
        responseCode = "501",
        description = "This endpoint is not yet implemented.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE_SYNC])
  @PreAuthorize("hasAnyAuthority('${ROLE_PRISONER_FINANCE_SYNC}')")
  fun updateSubscription(
    @Suppress("UNUSED_PARAMETER")
    @PathVariable subscriptionId: UUID,
    @Suppress("UNUSED_PARAMETER")
    @RequestBody
    @Valid request: SyncUpdateSubscriptionRequest,
  ): ResponseEntity<SyncUpdateSubscriptionResponse> = ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(
    SyncUpdateSubscriptionResponse(
      endDate = LocalDate.now(),
    ),
  )
}
