package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.subscriptions

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncCreateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncUpdateSubscriptionRequest
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SubscriptionsIntegrationTest : IntegrationTestBase() {

  @Nested
  inner class SyncSubscription {

    @Test
    fun `should return a 501 when the sync subscription endpoint is called and not implemented`() {
      val syncCreateSubscriptionRequest = SyncCreateSubscriptionRequest(
        legacyPaymentProfileId = 123456789L,
        amount = BigDecimal(10.0),
        dayOfPayment = DayOfWeek.MONDAY,
        startDate = LocalDate.now(),
        endDate = LocalDate.now().plusWeeks(1),
        subscriptionType = "TELE",
        reference = "",
        createdBy = "USER",
        createdAt = LocalDateTime.now(),
        prisonId = "LEI",
        prisonNumber = "A2222AA",
        subAccountCode = 2101,
      )

      webTestClient.post().uri("/sync/subscriptions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncCreateSubscriptionRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }
  }

  @Nested
  inner class UpdateSubscription {

    @Test
    fun `should return a 501 when the update subscription endpoint is called and not implemented`() {
      val subscriptionId = UUID.randomUUID()

      val syncUpdateSubscriptionRequest = SyncUpdateSubscriptionRequest(
        endDate = LocalDate.now(),
      )

      webTestClient.post().uri("/sync/subscriptions/$subscriptionId/update")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(syncUpdateSubscriptionRequest)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }
  }
}
