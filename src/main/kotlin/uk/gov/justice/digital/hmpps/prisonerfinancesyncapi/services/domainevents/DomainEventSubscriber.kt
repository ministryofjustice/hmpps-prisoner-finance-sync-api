package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.domainevents

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.PrisonerService

data class HmppsDomainEvent(
  val eventType: String,
  val additionalInformation: AdditionalInformation,
)

data class AdditionalInformation(
  val nomsNumber: String,
  val removedNomsNumber: String,
  val reason: String? = null,
)

data class Event(
  @SerializedName("Message")
  val message: String,
)

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
          prisonerService.mergePrisonerNumber(
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
