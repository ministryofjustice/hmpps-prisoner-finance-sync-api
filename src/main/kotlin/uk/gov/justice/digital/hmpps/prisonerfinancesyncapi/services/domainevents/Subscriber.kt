package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.domainevents

import com.google.gson.Gson
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

data class HmppsDomainEvent(
  val eventType: String,
  val additionalInformation: AdditionalInformation,
)

data class AdditionalInformation(
  val nomsNumber: String,
  val removedNomsNumber: String? = null,
  val reason: String? = null,
)

interface EventSubscriber

data class Event(val Message: String)

@Service
@Profile("!test")
class DomainEventSubscriber(
  private val gson: Gson,
  private val prisonerEvent: PrisonerEvent,
) : EventSubscriber {

  @SqsListener("domainevents", factory = "hmppsQueueContainerFactoryProxy")
  fun handleEvents(requestJson: String?) {
    val event = gson.fromJson(requestJson, Event::class.java)
    with(gson.fromJson(event.Message, HmppsDomainEvent::class.java)) {
      when (eventType) {
        "prison-offender-events.prisoner.merged" -> {
          log.info("Merged event: $event")
          prisonerEvent.mergeAccounts(
            additionalInformation.removedNomsNumber!!,
            additionalInformation.nomsNumber,
          )
        }
        else -> log.error("Unexpected event type: $eventType")
      }
    }
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
