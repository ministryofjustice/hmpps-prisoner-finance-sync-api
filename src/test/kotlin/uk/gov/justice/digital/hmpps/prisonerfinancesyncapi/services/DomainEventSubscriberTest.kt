package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import ch.qos.logback.classic.Level
import com.google.gson.Gson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.domainevents.DomainEventSubscriber
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.mockLogger

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

class DomainEventSubscriberTest {
  private val prisonerAccountMergeService: PrisonerAccountMergeService = mock()
  private val removedPrisoner = "AAA123"
  private val targetPrisoner = "BBB123"
  private val gson = Gson()
  private val domainEventSubscriber = DomainEventSubscriber(gson, prisonerAccountMergeService)

  @Test
  fun `should call consolidateAccounts with correct order when event is prisoner merged`() {
    val logger = mockLogger()
    val eventString = makePrisonerMergeEvent(removedPrisoner, targetPrisoner)

    domainEventSubscriber.handleEvents(eventString)

    verify(prisonerAccountMergeService).consolidateAccounts(removedPrisoner, targetPrisoner)

    assert(
      logger.events.any {
        it.formattedMessage.contains(removedPrisoner) &&
          it.formattedMessage.contains(targetPrisoner) &&
          it.level == Level.INFO
      },
    )
  }

  @Test
  fun `should not call merge service and log warn for unhandled event types`() {
    val logger = mockLogger()
    val unexpectedType = "some.other.event"
    val eventString = makePrisonerMergeEvent(removedPrisoner, targetPrisoner, eventType = unexpectedType)

    domainEventSubscriber.handleEvents(eventString)

    verify(prisonerAccountMergeService, never()).consolidateAccounts(any(), any())

    assert(
      logger.events.any {
        it.formattedMessage.contains("unexpected event type") &&
          it.formattedMessage.contains(unexpectedType) &&
          it.level == Level.WARN
      },
    )
  }

  @Test
  fun `should re-throw exceptions to trigger SQS retry when merge service fails`() {
    val eventString = makePrisonerMergeEvent(removedPrisoner, targetPrisoner)

    whenever(prisonerAccountMergeService.consolidateAccounts(removedPrisoner, targetPrisoner))
      .doThrow(RuntimeException("Boom"))

    assertThrows<RuntimeException> {
      domainEventSubscriber.handleEvents(eventString)
    }
  }

  @Test
  fun `should throw exception when JSON is malformed to ensure message goes to DLQ`() {
    val malformedJson = "{ \"invalid\": \"json\" " // Missing closing brace

    assertThrows<Exception> {
      domainEventSubscriber.handleEvents(malformedJson)
    }
  }
}
