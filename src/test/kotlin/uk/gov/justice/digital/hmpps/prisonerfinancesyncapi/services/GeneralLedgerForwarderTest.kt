package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException

@ExtendWith(MockitoExtension::class)
class GeneralLedgerForwarderTest {

  private lateinit var listAppender: ListAppender<ILoggingEvent>

  private lateinit var generalLedgerForwarder: GeneralLedgerForwarder

  private val matchingPrisonerId = "A1234AA"

  private val logger = LoggerFactory.getLogger(GeneralLedgerForwarder::class.java) as Logger

  @BeforeEach
  fun setup() {
    listAppender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(listAppender)
    generalLedgerForwarder = GeneralLedgerForwarder(true, listOf(matchingPrisonerId))
  }

  @AfterEach
  fun tearDown() {
    logger.detachAppender(listAppender)
  }

  @Test
  fun `should log configuration on startup`() {
    val logs = listAppender.list.map { it.formattedMessage }

    assertThat(logs).anyMatch {
      it.contains("GeneralLedgerSwitchManager initialized. Enabled: ${true}. Test Prisoner IDs: ${listOf(matchingPrisonerId)}")
    }
  }

  @Test
  fun `should call GL and return something when flags are enabled and prisoner matches`() {
    val result = generalLedgerForwarder.executeIfEnabled(
      "Test",
      matchingPrisonerId,
      { true },
    )

    assertThat(result).isTrue()
  }

  @Test
  fun `should handle and log exception when it's thrown by GL when reconciling a prisoner`() {
    val result: String? = generalLedgerForwarder.executeIfEnabled(
      "Error in GL",
      matchingPrisonerId,
      {
        throw RuntimeException("Expected Exception")
      },
    )

    assertThat(result).isNull()

    val logs = listAppender.list.map { it.formattedMessage }
    assertThat(logs).anyMatch {
      it.contains("Error in GL")
    }
  }

  @Test
  fun `should handle and log HTTP exception when it's thrown by GL when reconciling a prisoner`() {
    val e = mock<WebClientResponseException> {
      on { statusCode } doReturn HttpStatus.BAD_REQUEST
      on { responseBodyAsString } doReturn "Error body"
      on { request } doReturn null
    }

    val result: String? = generalLedgerForwarder.executeIfEnabled(
      "Error in GL",
      matchingPrisonerId,
      {
        throw e
      },
    )

    assertThat(result).isNull()

    val logs = listAppender.list.map { it.formattedMessage }
    assertThat(logs).anyMatch {
      it.contains("HTTP Error to General Ledger. HTTP ${e.statusCode} - Body: ${e.responseBodyAsString}, ${e.request}")
    }
  }

  @Test
  fun `should not call GL when flag is disabled`() {
    generalLedgerForwarder = GeneralLedgerForwarder(false, listOf(matchingPrisonerId))

    val funCall = mock<() -> Boolean>()

    val result: Boolean? = generalLedgerForwarder.executeIfEnabled(
      "Error in GL",
      matchingPrisonerId,
      funCall,
    )

    assertThat(result).isNull()
    verify(funCall, never()).invoke()
  }

  @Test
  fun `should not call GL when prisonerId doesn't match`() {
    generalLedgerForwarder = GeneralLedgerForwarder(true, listOf("UNKNOWN_ID"))

    val funCall = mock<() -> Boolean>()

    val result: Boolean? = generalLedgerForwarder.executeIfEnabled(
      "Error in GL",
      matchingPrisonerId,
      funCall,
    )

    assertThat(result).isNull()
    verify(funCall, never()).invoke()
  }

  @Test
  fun `should not call GL for multiple whitelisted prisonerIds`() {
    val multiplePrisonerIds = listOf("A1234AA", "A1234AB")
    generalLedgerForwarder = GeneralLedgerForwarder(true, multiplePrisonerIds)

    for (prisonerId in multiplePrisonerIds) {
      val result = generalLedgerForwarder.executeIfEnabled(
        "Test",
        prisonerId,
        { true },
      )

      assertThat(result).isTrue()
    }
  }
}
