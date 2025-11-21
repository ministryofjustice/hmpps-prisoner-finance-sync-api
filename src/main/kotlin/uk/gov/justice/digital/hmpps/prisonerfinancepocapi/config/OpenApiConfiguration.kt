package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

const val TAG_NOMIS_SYNC = "NOMIS Sync"
const val TAG_PRISONER_ACCOUNTS = "Prisoner trust accounts"
const val TAG_PRISON_ACCOUNTS = "Prison accounts"
const val TAG_PRISON_REPORTS = "Prison reports"
const val ROLE_PRISONER_FINANCE_SYNC = "ROLE_PRISONER_FINANCE_SYNC__RW"

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {

  private val version: String = buildProperties.version

  @Bean
  fun customOpenAPI(): OpenAPI = OpenAPI()
    .servers(serviceServers())
    .info(apiInfo())
    .components(securityComponents())
    .addSecurityItem(SecurityRequirement().addList("bearer-jwt"))
    .tags(apiTags())

  private fun serviceServers(): List<Server> = listOf(
    Server().url("https://prisoner-finance-poc-api-dev.hmpps.service.justice.gov.uk/").description("Development"),
    Server().url("http://localhost:8080").description("Local"),
  )

  private fun apiInfo(): Info = Info()
    .title("HMPPS Prisoner Finance API - ALPHA")
    .version(version)
    .description(apiDescription())
    .contact(apiContact())

  private fun apiDescription(): String = """
    |API for retrieving and managing transactions and balance information related to prisoner finance.
    |
    |## Authentication
    |This API uses **OAuth2 with JWTs**. Pass your JWT in the `Authorization` header using the `Bearer` scheme.
    |**Important:** This service is designed for client tokens only; user tokens should not be used.
    |
    |## Authorization
    |Access to API endpoints is controlled by roles. The required roles are documented with each endpoint.
    |Integrations should request one of the following roles based on their needs:
    |
    |* `ROLE_PRISONER_FINANCE__RO`: Grants **read-only access** (e.g., retrieving transactions, balances).
    |* `ROLE_PRISONER_FINANCE__RW`: Grants **read/write access** (e.g., creating transactions, adding amendments).
    |* `ROLE_PRISONER_FINANCE_SYNC__RW`: Grants **read/write access** to the NOMIS sync endpoints.
  """.trimMargin()

  private fun apiContact(): Contact = Contact()
    .name("HMPPS Digital Studio")
    .email("feedback@digital.justice.gov.uk")

  private fun securityComponents(): Components = Components().addSecuritySchemes(
    "bearer-jwt",
    SecurityScheme()
      .type(SecurityScheme.Type.HTTP)
      .scheme("bearer")
      .bearerFormat("JWT")
      .`in`(SecurityScheme.In.HEADER)
      .name("Authorization"),
  )

  private fun apiTags(): List<Tag> = listOf(
    Tag()
      .name(TAG_NOMIS_SYNC)
      .description("Endpoints exclusively for NOMIS synchronization. Do not use for other client integrations."),
  )
}
