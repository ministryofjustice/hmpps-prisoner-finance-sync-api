package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestConstructor
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.config.PostgresContainer
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.config.registerPostgresProperties

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class RepositoryTestBase {

  companion object {
    private val postgres = PostgresContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun testcontainers(registry: DynamicPropertyRegistry) {
      registry.registerPostgresProperties(postgres)
    }
  }
}
