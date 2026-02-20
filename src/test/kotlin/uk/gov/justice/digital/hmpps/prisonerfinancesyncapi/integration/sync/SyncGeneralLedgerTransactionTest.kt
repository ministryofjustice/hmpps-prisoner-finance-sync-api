package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.sync

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniqueCaseloadId
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class SyncGeneralLedgerTransactionTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `401 unauthorised`() {
    webTestClient
      .post()
      .uri("/sync/general-ledger-transactions")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - does not have the right role`() {
    webTestClient
      .post()
      .uri("/sync/general-ledger-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
      .bodyValue(createSyncGeneralLedgerTransactionRequest())
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `201 Created - when transaction is new`() {
    val newTransactionRequest = createSyncGeneralLedgerTransactionRequest()

    webTestClient
      .post()
      .uri("/sync/general-ledger-transactions")
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
  fun `400 Bad Request - when amount has more than 2 decimal places`() {
    val newTransactionRequest = createSyncGeneralLedgerTransactionRequest(
      uniqueCaseloadId(),
      "GJ",
      listOf(
        GeneralLedgerEntry(entrySequence = 1, code = 1101, postingType = "DR", amount = BigDecimal("50.001")),
        GeneralLedgerEntry(entrySequence = 2, code = 2503, postingType = "CR", amount = BigDecimal("50.001")),
      ),
    )

    webTestClient
      .post()
      .uri("/sync/general-ledger-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(newTransactionRequest)
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `400 Bad Request - missing required requestId`() {
    val invalidMap = mapOf(
      "transactionId" to 1234,
      "caseloadId" to "GMI",
      "description" to "General Ledger Account Transfer",
      "transactionType" to "GJ",
      "transactionTimestamp" to LocalDateTime.now(),
      "createdAt" to LocalDateTime.now(),
      "createdBy" to "TESTUSER",
      "createdByDisplayName" to "Test User",
      "generalLedgerEntries" to emptyList<Any>(),
    )
    val invalidJson = objectMapper.writeValueAsString(invalidMap)

    webTestClient
      .post()
      .uri("/sync/general-ledger-transactions")
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
    val request = createSyncGeneralLedgerTransactionRequest().copy(createdBy = longString)

    webTestClient
      .post()
      .uri("/sync/general-ledger-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(request)
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failure")
      .jsonPath("$.developerMessage")
      .isEqualTo("Validation failed: createdBy: Created by must be supplied and be <= 32 characters")
  }

  @Test
  fun `400 Bad Request - invalid caseloadId`() {
    val caseloadId = "-A-"

    val request = createSyncGeneralLedgerTransactionRequest(caseloadId = caseloadId)

    webTestClient
      .post()
      .uri("/sync/general-ledger-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(request)
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage")
      .isEqualTo("Validation failed: caseloadId: prisonId must be 3 alphanumeric characters")
  }

  @Test
  fun `400 Bad Request - invalid transactionType`() {
    val transactionType = "£DRAIN"

    val request = createSyncGeneralLedgerTransactionRequest(transactionType = transactionType)

    webTestClient
      .post()
      .uri("/sync/general-ledger-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(request)
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value<String> { message ->
        assertThat(message).contains("Transaction Type must be 1-19 capital alphanumeric characters or underscores")
      }
  }

  private fun createSyncGeneralLedgerTransactionRequest(
    caseloadId: String = "GMI",
    transactionType: String = "AD",
    generalLedgerEntries: List<GeneralLedgerEntry> = listOf(
      GeneralLedgerEntry(entrySequence = 1, code = 1101, postingType = "DR", amount = BigDecimal("50.00")),
      GeneralLedgerEntry(entrySequence = 2, code = 2503, postingType = "CR", amount = BigDecimal("50.00")),
    ),
  ): SyncGeneralLedgerTransactionRequest = SyncGeneralLedgerTransactionRequest(
    transactionId = 19228028,
    requestId = UUID.randomUUID(),
    description = "General Ledger Account Transfer",
    reference = "REF12345",
    caseloadId = caseloadId,
    transactionType = transactionType,
    transactionTimestamp = LocalDateTime.now(),
    createdAt = LocalDateTime.now(),
    createdBy = "JD12345",
    createdByDisplayName = "J Doe",
    lastModifiedAt = null,
    lastModifiedBy = null,
    lastModifiedByDisplayName = null,
    generalLedgerEntries = generalLedgerEntries,
  )
}
