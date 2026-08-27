package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.holds

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.time.Instant


@ExtendWith(MockitoExtension::class)
class HoldsIntegrationTest : IntegrationTestBase() {

  @Nested
  @DisplayName("postHolds")
  inner class PostHolds {

    @Test
    fun `should return a 201 when a hold is created`(){

      val holdRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = "2102",
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal("99.99")
      )

      webTestClient
        .post()
        .uri("/sync/holds")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(holdRequest)
        .exchange()
        .expectStatus().isCreated

    }
  }
}