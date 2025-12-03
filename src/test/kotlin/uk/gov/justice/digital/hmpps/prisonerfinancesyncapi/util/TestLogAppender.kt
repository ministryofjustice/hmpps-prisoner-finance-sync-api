package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.util

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.domainevents.DomainEventSubscriber

class TestLogAppender : AppenderBase<ILoggingEvent>() {
  val events = mutableListOf<ILoggingEvent>()

  override fun append(event: ILoggingEvent) {
    events.add(event)
  }
}

fun mockLogger(): TestLogAppender {
  val logger = LoggerFactory.getLogger(
    DomainEventSubscriber::class.java,
  ) as ch.qos.logback.classic.Logger
  val testLogAppender = TestLogAppender().apply {
    context = logger.loggerContext
    start()
  }
  logger.addAppender(testLogAppender)
  return testLogAppender
}
