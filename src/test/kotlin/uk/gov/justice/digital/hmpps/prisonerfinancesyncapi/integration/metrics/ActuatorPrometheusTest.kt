package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.metrics

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase

class ActuatorPrometheusTest : IntegrationTestBase() {

  @Test
  fun `Actuator prometheus endpoint reports ok`() {
    stubPingWithResponse(200)

    webTestClient.get()
      .uri("/metrics")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
  }
}
