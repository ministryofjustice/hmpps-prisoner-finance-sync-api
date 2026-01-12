package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.config

import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.IOException
import java.net.ServerSocket

object PostgresContainer {
  val instance: PostgreSQLContainer<Nothing>? by lazy { startPostgresqlContainer() }

  private fun startPostgresqlContainer(): PostgreSQLContainer<Nothing>? {
    if (isPostgresRunning()) {
      log.warn("Using existing Postgres database")
      return null
    }
    log.info("Creating a Postgres database")
    return PostgreSQLContainer<Nothing>("postgres:18").apply {
      withEnv("HOSTNAME_EXTERNAL", "localhost")
      withDatabaseName("pf-test-db")
      withUsername("pf-test-db")
      withPassword("pf-test-db")
      setWaitStrategy(Wait.forListeningPort())
      withReuse(true)

      start()
    }
  }

  private fun isPostgresRunning(): Boolean = try {
    ServerSocket(5432).use { false }
  } catch (e: IOException) {
    true
  }

  private val log = LoggerFactory.getLogger(this::class.java)
}

fun DynamicPropertyRegistry.registerPostgresProperties(container: PostgreSQLContainer<Nothing>?) {
  if (container != null) {
    container.run {
      add("spring.datasource.url") { jdbcUrl }
      add("spring.datasource.username") { username }
      add("spring.datasource.password") { password }
    }
  } else {
    add("spring.datasource.url") { "jdbc:postgresql://localhost:5432/nomis_sync" }
    add("spring.datasource.username") { "postgres" }
    add("spring.datasource.password") { "postgres" }
  }
}
