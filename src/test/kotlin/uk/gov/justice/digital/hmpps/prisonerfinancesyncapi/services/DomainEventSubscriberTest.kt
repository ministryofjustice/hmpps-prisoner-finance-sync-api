package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.google.gson.Gson
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.slf4j.LoggerFactory
import org.springframework.boot.test.autoconfigure.json.JsonTest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.domainevents.Event
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.domainevents.DomainEventSubscriber

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

@JsonTest
class DomainEventSubscriberTest {
  private val prisonerService: PrisonerService = mock()
  private val mockUnexpectedEventType = "UnexceptedEventType"
  private val mockPrisonerNumberA = "AAA123"
  private val mockPrisonerNumberB = "BBB123"
  val gson = Gson()
  private val domainEventSubscriber = DomainEventSubscriber(gson, prisonerService)

  class TestAppender : AppenderBase<ILoggingEvent>() {
    val events = mutableListOf<ILoggingEvent>()

    override fun append(event: ILoggingEvent) {
      events.add(event)
    }
  }

  fun mock_logger(): TestAppender {
    val logger = LoggerFactory.getLogger(
      DomainEventSubscriber::class.java,
    ) as ch.qos.logback.classic.Logger
    val testAppender = TestAppender().apply {
      context = logger.loggerContext
      start()
    }
    logger.addAppender(testAppender)
    return testAppender
  }

  @Test
  fun `should call merge when event is prison-offender-events prisoner merged`() {
    val logger = mock_logger()
    val mockEventString = makePrisonerMergeEvent(
      mockPrisonerNumberA,
      mockPrisonerNumberB,
    )
    val mockEvent = gson.fromJson(mockEventString, Event::class.java)

    domainEventSubscriber.handleEvents(mockEventString)

    verify(prisonerService).merge(mockPrisonerNumberA, mockPrisonerNumberB)
    assert(logger.events.any { it.formattedMessage.contains("Merged event: $mockEvent") })
  }

  @Test
  fun `should not call merge and should log error when eventType is not prison-offender-events prisoner merged`() {
    val logger = mock_logger()
    val mockEventString = makePrisonerMergeEvent(
      mockPrisonerNumberA,
      mockPrisonerNumberB,
      eventType = mockUnexpectedEventType,
    )
    val mockEvent = gson.fromJson(mockEventString, Event::class.java)

    domainEventSubscriber.handleEvents(mockEventString)

    verify(prisonerService, never()).merge(mockPrisonerNumberA, mockPrisonerNumberB)
    assert(logger.events.any { it.formattedMessage.contains("Unexpected event type: $mockUnexpectedEventType for event: $mockEvent") })
  }
}
