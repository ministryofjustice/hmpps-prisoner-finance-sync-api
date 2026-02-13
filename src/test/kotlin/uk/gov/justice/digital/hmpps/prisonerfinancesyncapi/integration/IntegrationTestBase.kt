package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.config.PostgresContainer
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.config.registerPostgresProperties
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.util.EnumSet

@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
@SpringBootTest(
  webEnvironment = RANDOM_PORT,
  properties = ["spring.autoconfigure.exclude=uk.gov.justice.hmpps.sqs.HmppsSqsConfiguration"],
)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
abstract class IntegrationTestBase {

  @Autowired
  protected lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  protected fun stubPingWithResponse(status: Int) {
    hmppsAuth.stubHealthPing(status)
    generalLedgerApi.stubHealthPing(status)
  }

  companion object {

    private val postgres = PostgresContainer.instance

    @Suppress("unused")
    @JvmStatic
    @DynamicPropertySource
    fun testcontainers(registry: DynamicPropertyRegistry) {
      registry.registerPostgresProperties(postgres)
    }

    // Forcing WebTestClient ObjectMapper to use Jackson.
    // At the moment, it depends on Jackson2.
    // This is required because otherwise the testclient uses Doubles instead of Decimals.
    init {
      val jackson2Mapper = ObjectMapper()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)

      Configuration.setDefaults(
        object : Configuration.Defaults {
          private val jsonProvider = JacksonJsonProvider(jackson2Mapper)
          private val mappingProvider = JacksonMappingProvider(jackson2Mapper)

          override fun jsonProvider() = jsonProvider
          override fun mappingProvider() = mappingProvider
          override fun options() = EnumSet.noneOf(Option::class.java)
        },
      )
    }
  }
}
