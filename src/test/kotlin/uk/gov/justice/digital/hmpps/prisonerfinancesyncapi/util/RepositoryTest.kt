package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.util

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.TestPropertySource

// run JPA tests against the real database to avoid missing bugs arising from SQL syntax
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
  properties = [
    "spring.flyway.locations=classpath:/db/migrations/common,classpath:/db/migrations/postgres",
  ],
)
annotation class RepositoryTest
