package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.sync

import com.nimbusds.jose.shaded.gson.Gson
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.PrisonerService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.domainevents.DomainEventSubscriber
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.sendMessage

@SpringBootTest
@ActiveProfiles("test")
class DomainEventSubscriberQueueSpyBeanTest {
  @Autowired
  lateinit var hmppsQueueService: HmppsQueueService

  @MockitoSpyBean
  lateinit var subscriber: DomainEventSubscriber

  @MockitoSpyBean
  lateinit var prisonerService: PrisonerService

  private val gson = Gson()

  fun makePrisonerMergeEvent(removedPrisonerNumber: String, prisonerNumber: String, eventType: String = "prison-offender-events.prisoner.merged") =
    """
    {
        "Type": "Notification",
        "MessageId": "5b90ee7d-67bc-5959-a4d8-b7d420180853",
        "Message":"{\"eventType\":\"$eventType\",\"version\":\"1.0\", \"occurredAt\":\"2020-02-12T15:14:24.125533+00:00\", \"publishedAt\":\"2020-02-12T15:15:09.902048716+00:00\", \"description\":\"A prisoner has been merged from $removedPrisonerNumber to $prisonerNumber\", \"additionalInformation\":{\"nomsNumber\":\"$prisonerNumber\", \"removedNomsNumber\":\"$removedPrisonerNumber\", \"reason\":\"MERGE\"}}",
        "Timestamp": "2021-09-01T09:18:28.725Z",
        "MessageAttributes": {
            "eventType": {
                "Type": "String",
                "Value": "$eventType"
            }
        }
    }
    """.trimIndent()

  @Test
  fun `should process merged event in DomainEventSubscriber and PrisonerService`() {
    val queue = hmppsQueueService.findByQueueId("domainevents")!!

    val json = makePrisonerMergeEvent("AAA123", "BBB123")

    queue.sendMessage(eventType = "prison-offender-events.prisoner.merged", event = json)

    val captor = argumentCaptor<String>()

    await untilAsserted {
      verify(subscriber).handleEvents(captor.capture())
    }

    await untilAsserted {
      verify(prisonerService).merge("AAA123", "BBB123")
    }

    val messageReceived = captor.firstValue

    Assertions.assertThat(messageReceived).contains("prisoner.merged")
    Assertions.assertThat(messageReceived).contains("nomsNumber")
    Assertions.assertThat(messageReceived).contains("AAA123")
    Assertions.assertThat(messageReceived).contains("removedNomsNumber")
    Assertions.assertThat(messageReceived).contains("BBB123")
  }
}
