package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.domainevents

import com.google.gson.Gson
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.domainevents.Event
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.domainevents.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.PrisonerAccountMergeService

@Service
class DomainEventSubscriber(
  private val gson: Gson,
  private val prisonerAccountMergeService: PrisonerAccountMergeService,
) {

  @SqsListener("domainevents", factory = "hmppsQueueContainerFactoryProxy")
  fun handleEvents(requestJson: String?) {
    try {
      val event = gson.fromJson(requestJson, Event::class.java)
      val domainEvent = gson.fromJson(event.message, HmppsDomainEvent::class.java)

      when (domainEvent.eventType) {
        PRISONER_MERGE_EVENT_TYPE -> {
          log.info("Processing merge for ${domainEvent.additionalInformation.removedNomsNumber} -> ${domainEvent.additionalInformation.nomsNumber}")
          prisonerAccountMergeService.consolidateAccounts(
            domainEvent.additionalInformation.removedNomsNumber,
            domainEvent.additionalInformation.nomsNumber,
          )
        }
        else -> {
          log.warn("Ignored unexpected event type: ${domainEvent.eventType}")
        }
      }
    } catch (e: Exception) {
      log.error("Failed to process domain event. Message will be retried. Payload: $requestJson", e)
      throw e
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    const val PRISONER_MERGE_EVENT_TYPE = "prison-offender-events.prisoner.merged"
  }
}
