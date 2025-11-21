package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.util

import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.TestPropertySource

// run JPA tests against the real database to avoid missing bugs arising from SQL syntax
@DataJpaTest
@TestPropertySource(
  properties = [
    "spring.flyway.locations=classpath:/db/migrations/common,classpath:/db/migrations/h2",
  ],
)
annotation class RepositoryTest
