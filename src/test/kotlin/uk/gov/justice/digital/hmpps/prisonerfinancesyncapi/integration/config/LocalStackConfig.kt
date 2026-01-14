package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.config

import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.IOException
import java.net.ServerSocket

object LocalStackConfig {
  private val log = LoggerFactory.getLogger(this::class.java)
  val instance by lazy { startLocalstackIfNotRunning() }

  fun setLocalStackProperties(localStackContainer: LocalStackContainer?, registry: DynamicPropertyRegistry) {
    if (localStackContainer != null) {
      val localstackUrl = localStackContainer.getEndpointOverride(LocalStackContainer.Service.SNS).toString()
      val region = localStackContainer.region
      registry.add("hmpps.sqs.localstackUrl") { localstackUrl }
      registry.add("hmpps.sqs.region") { region }
    } else {
      registry.add("hmpps.sqs.localstackUrl") { "http://localhost:4566" }
      registry.add("hmpps.sqs.region") { "eu-west-2" }
    }
  }

  private fun startLocalstackIfNotRunning(): LocalStackContainer? {
    if (localstackIsRunning()) return null
    val logConsumer = Slf4jLogConsumer(log).withPrefix("localstack")
    return LocalStackContainer(
      DockerImageName.parse("localstack/localstack").withTag("4"),
    ).apply {
      withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS)
      withEnv("DEFAULT_REGION", "eu-west-2")
      waitingFor(
        Wait.forLogMessage(".*Ready.*", 1),
      )
      start()
      followOutput(logConsumer)
    }
  }

  private fun localstackIsRunning(): Boolean = try {
    val serverSocket = ServerSocket(4566)
    log.info("Localstack is not running, starting testContainer")
    serverSocket.localPort == 0
  } catch (e: IOException) {
    log.warn("Localstack is already running, using existing container")
    true
  }
}
