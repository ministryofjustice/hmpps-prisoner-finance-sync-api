package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.config.LocalStackConfig
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.config.PostgresContainer
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.config.registerPostgresProperties
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsConfiguration
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

@Import(HmppsSqsConfiguration::class)
class SqsIntegrationTestBase : IntegrationTestBase() {

  companion object {
    @Suppress("unused")
    @JvmStatic
    @DynamicPropertySource
    fun dynamicProperties(registry: DynamicPropertyRegistry) {
      registry.registerPostgresProperties(PostgresContainer.instance)
      LocalStackConfig.setLocalStackProperties(LocalStackConfig.instance!!, registry)
    }
  }

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  lateinit var objectMapper: ObjectMapper

  protected val domainEventQueue by lazy {
    hmppsQueueService.findByQueueId("domainevents")
      ?: throw MissingQueueException("HmppsQueue domainevents not found")
  }

  protected val domainEventsTopic by lazy {
    hmppsQueueService.findByTopicId("domainevents")
      ?: throw MissingQueueException("HmppsTopic domainevents not found")
  }

  protected val domainEventsTopicSnsClient by lazy { domainEventsTopic.snsClient }
  protected val domainEventsTopicArn by lazy { domainEventsTopic.arn }

  @BeforeEach
  fun cleanQueue() {
    domainEventQueue.sqsClient.purgeQueue(
      PurgeQueueRequest.builder().queueUrl(domainEventQueue.queueUrl).build(),
    )
    domainEventQueue.sqsClient.countMessagesOnQueue(domainEventQueue.queueUrl).get()
  }

  protected fun jsonString(any: Any) = objectMapper.writeValueAsString(any) as String
}
