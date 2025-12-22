package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.account

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase

class GetPrisonsAccountTest : IntegrationTestBase() {
  @ParameterizedTest
  @CsvSource(
    "hello there",
    "a-dash",
    "awaaaaaaaaaytooooloooongstring",
    "#123",
    "hello''pg_sleep(10)",
  )
  fun `Get prisons should return 400 Bad Request when parameters are invalid`(invalidParameter: String) {
    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts", invalidParameter)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isBadRequest
  }

  @ParameterizedTest
  @CsvSource(
    "KMI",
  )
  fun `Get prisons should return 200 when parameters valid`(invalidParameter: String) {
    webTestClient
      .get()
      .uri("/prisons/{prisonId}/accounts", invalidParameter)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items").isEmpty()
  }
}
