package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.sync

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.createSyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniqueCaseloadId
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniquePrisonNumber
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class SyncOffenderTransactionTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Autowired lateinit var nomisSyncPayloadRepository: NomisSyncPayloadRepository

  @BeforeEach
  fun setup() {
    generalLedgerApi.resetAll()
    hmppsAuth.stubGrantToken()
  }

  @AfterEach
  fun tearDown() {
    nomisSyncPayloadRepository.deleteAll()
  }

  @Test
  fun `401 unauthorised`() {
    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - does not have the right role`() {
    val caseloadId = uniqueCaseloadId()
    val prisonNumber = uniquePrisonNumber()

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
      .bodyValue(createSyncOffenderTransactionRequest(caseloadId, prisonNumber))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `201 Created - when transaction is new`() {
    val caseloadId = uniqueCaseloadId()
    val prisonNumber = uniquePrisonNumber()

    val newTransactionRequest = createSyncOffenderTransactionRequest(
      caseloadId,
      prisonNumber,
      generalLedgerEntries = listOf(
        GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = BigDecimal("162.00")),
        GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = BigDecimal("162.00")),
      ),
    )

    val parentAccountUuid = UUID.randomUUID()
    val cashSubAccountUuid = UUID.randomUUID()
    val spendsSubAccountUuid = UUID.randomUUID()

    generalLedgerApi.stubGetAccount(
      returnUuid = parentAccountUuid,
      reference = prisonNumber,
      subAccounts = listOf(
        SubAccountResponse(
          id = cashSubAccountUuid,
          reference = "CASH",
          parentAccountId = parentAccountUuid,
          createdBy = "",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = spendsSubAccountUuid,
          reference = "SPENDS",
          parentAccountId = parentAccountUuid,
          createdBy = "",
          createdAt = Instant.now(),
        ),
      ),
    )

    generalLedgerApi.stubPostTransaction(
      debtorSubAccountUuid = cashSubAccountUuid.toString(),
      creditorSubAccountUuid = spendsSubAccountUuid.toString(),
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(newTransactionRequest)
      .exchange()
      .expectStatus().isCreated
      .expectBody()
      .jsonPath("$.action").isEqualTo("CREATED")
  }

  @Test
  fun `400 Bad Request - when transaction amount has more than 2 decimal places`() {
    val caseloadId = uniqueCaseloadId()
    val prisonNumber = uniquePrisonNumber()

    val newTransactionRequest = createSyncOffenderTransactionRequest(caseloadId, prisonNumber, amount = BigDecimal("162.005"))

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(newTransactionRequest)
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `400 Bad Request - when prisonNumber is invalid`() {
    val caseloadId = uniqueCaseloadId()
    val prisonNumber = "R2  D2"

    val newTransactionRequest = createSyncOffenderTransactionRequest(caseloadId, prisonNumber)

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(newTransactionRequest)
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value<String> { message ->
        assertThat(message).startsWith("Validation failed: offenderTransactions[0].offenderDisplayId: prisonerId must be 7 alphanumeric characters")
      }
  }

  @Test
  fun `400 Bad Request - when caseloadId is invalid`() {
    val caseloadId = "-A-"
    val prisonNumber = uniquePrisonNumber()

    val newTransactionRequest = createSyncOffenderTransactionRequest(caseloadId, prisonNumber)

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(newTransactionRequest)
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value<String> { message ->
        assertThat(message).startsWith("Validation failed: caseloadId: prisonId must be 3 alphanumeric characters")
      }
  }

  @Test
  fun `400 Bad Request - when transactionType is invalid`() {
    val caseloadId = uniqueCaseloadId()
    val prisonNumber = uniquePrisonNumber()

    val newTransactionRequest = createSyncOffenderTransactionRequest(caseloadId, prisonNumber, "£DRAIN")

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(newTransactionRequest)
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value<String> { message ->
        assertThat(message).contains("Transaction Type must be 1-19 capital alphanumeric characters or underscores")
      }
  }

  @Test
  fun `400 Bad Request - when transaction ENTRY amount has more than 2 decimal places`() {
    val caseloadId = uniqueCaseloadId()
    val prisonNumber = uniquePrisonNumber()

    val newTransactionRequest = createSyncOffenderTransactionRequest(
      caseloadId,
      prisonNumber,
      "OT",
      listOf(
        GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = BigDecimal("162.005")),
        GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = BigDecimal("162.005")),
      ),
      amount = BigDecimal("162.00500"),
    )

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(newTransactionRequest)
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `400 Bad Request - missing required requestId`() {
    val caseloadId = uniqueCaseloadId()

    val invalidMap = mapOf(
      "transactionId" to 1234,
      "caseloadId" to caseloadId,
      "transactionTimestamp" to LocalDateTime.now(),
      "createdAt" to LocalDateTime.now(),
      "createdBy" to "TESTUSER",
      "createdByDisplayName" to "Test User",
      "offenderTransactions" to emptyList<Any>(),
    )
    val invalidJson = objectMapper.writeValueAsString(invalidMap)

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(invalidJson)
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").value<String> { message ->
        assertThat(message).startsWith("Invalid request body:")
      }
      .jsonPath("$.developerMessage").value<String> { message ->
        assertThat(message).startsWith("JSON parse error:")
        assertThat(message).contains("requestId due to missing")
      }
  }

  @Test
  fun `400 Bad Request - createdBy too long`() {
    val longString = "A".repeat(33)
    val caseloadId = uniqueCaseloadId()
    val prisonNumber = uniquePrisonNumber()

    val request = createSyncOffenderTransactionRequest(caseloadId, prisonNumber).copy(createdBy = longString)

    webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(request)
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failure")
      .jsonPath("$.developerMessage").isEqualTo("Validation failed: createdBy: Created by must be supplied and be <= 32 characters")
  }
}
