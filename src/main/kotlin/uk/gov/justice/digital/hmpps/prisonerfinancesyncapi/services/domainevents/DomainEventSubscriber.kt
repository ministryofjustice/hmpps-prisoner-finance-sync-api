package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.domainevents

import com.google.gson.Gson
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.domainevents.Event
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.domainevents.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.PrisonerService

@Service
class DomainEventSubscriber(
  private val gson: Gson,
  private val prisonerService: PrisonerService,
) {

  @SqsListener("domainevents", factory = "hmppsQueueContainerFactoryProxy")
  fun handleEvents(requestJson: String?) {
    val event = gson.fromJson(requestJson, Event::class.java)
    with(gson.fromJson(event.message, HmppsDomainEvent::class.java)) {
      when (eventType) {
        "prison-offender-events.prisoner.merged" -> {
          log.info("Merged event: $event")
          prisonerService.merge(
            additionalInformation.removedNomsNumber,
            additionalInformation.nomsNumber,
          )
        }
        else -> log.error("Unexpected event type: $eventType for event: $event")
      }
    }
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
