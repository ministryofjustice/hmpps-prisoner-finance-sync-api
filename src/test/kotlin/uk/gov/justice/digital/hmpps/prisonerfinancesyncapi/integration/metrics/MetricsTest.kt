package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.metrics

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase

class MetricsTest : IntegrationTestBase() {

  @Test
  fun `Metrics endpoint reports ok`() {
    stubPingWithResponse(200)

    webTestClient.get()
      .uri("/metrics")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
  }
}
