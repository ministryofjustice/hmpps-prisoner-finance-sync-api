package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.google.gson.Gson
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.domainevents.DomainEventSubscriber

fun makePrisonerMergeEvent(removedPrisonerNumber: String = "A4432FD", prisonerNumber: String) =
  """
    {
        "Type": "Notification",
        "MessageId": "5b90ee7d-67bc-5959-a4d8-b7d420180853",
        "Message":"{\"eventType\":\"prison-offender-events.prisoner.merged\",\"version\":\"1.0\", \"occurredAt\":\"2020-02-12T15:14:24.125533+00:00\", \"publishedAt\":\"2020-02-12T15:15:09.902048716+00:00\", \"description\":\"A prisoner has been merged from $removedPrisonerNumber to $prisonerNumber\", \"additionalInformation\":{\"nomsNumber\":\"$prisonerNumber\", \"removedNomsNumber\":\"$removedPrisonerNumber\", \"reason\":\"MERGE\"}}",
        "Timestamp": "2021-09-01T09:18:28.725Z",
        "MessageAttributes": {
            "eventType": {
                "Type": "String",
                "Value": "prison-offender-events.prisoner.merged"
            }
        }
    }
  """.trimIndent()

fun makeUnexpectedEventTypeJson(removedPrisonerNumber: String = "A4432FD", prisonerNumber: String) =
  """
    {
        "Type": "Notification",
        "MessageId": "5b90ee7d-67bc-5959-a4d8-b7d420180853",
        "Message":"{\"eventType\":\"fake-event-test\",\"version\":\"1.0\", \"occurredAt\":\"2020-02-12T15:14:24.125533+00:00\", \"publishedAt\":\"2020-02-12T15:15:09.902048716+00:00\", \"description\":\"A prisoner has been merged from $removedPrisonerNumber to $prisonerNumber\", \"additionalInformation\":{\"nomsNumber\":\"$prisonerNumber\", \"removedNomsNumber\":\"$removedPrisonerNumber\", \"reason\":\"MERGE\"}}",
        "Timestamp": "2021-09-01T09:18:28.725Z",
        "MessageAttributes": {
            "eventType": {
                "Type": "String",
                "Value": "prison-offender-events.prisoner.merged"
            }
        }
    }
  """.trimIndent()

@JsonTest
class DomainEventSubscriberTest(@Autowired gson: Gson) {
  private val prisonerEvent: PrisonerService = mock()
  private val domainEventSubscriber = DomainEventSubscriber(gson, prisonerEvent)

  @Test
  fun `calls mergePrisonerNumber when two prisoner records are merged`() {
    domainEventSubscriber.handleEvents(makePrisonerMergeEvent("A12345", "A23456"))
    verify(prisonerEvent).mergePrisonerNumber("A12345", "A23456")
  }

  @Test
  fun `mergePrisonerNumber is not called when eventType is not prison-offender-events prisoner merged`() {
    domainEventSubscriber.handleEvents(makeUnexpectedEventTypeJson("A12345", "A23456"))
    verify(prisonerEvent, never()).mergePrisonerNumber("A12345", "A23456")
  }
}
