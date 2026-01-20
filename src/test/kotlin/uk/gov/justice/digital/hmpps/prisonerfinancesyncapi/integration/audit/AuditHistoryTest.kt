package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.audit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.VALIDATION_MESSAGE_PRISON_ID
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.sync.SyncOffenderTransactionTest.Companion.createSyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt

class AuditHistoryTest : IntegrationTestBase() {

  @Test
  fun `Get History should return an empty list when there aren't any payloads`() {
    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", "XXX")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("content").isEqualTo(emptyList<Any>())
  }

  @Test
  fun `Get History should return Bad Request when prison number is invalid`() {
    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", "asdasdaassdadsa123123")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.status").isEqualTo(400)
      .jsonPath("$.userMessage")
      .value<String> {
        assertThat(it).contains(VALIDATION_MESSAGE_PRISON_ID)
      }
  }

  @Test
  fun `401 unauthorised`() {
    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", "XXX")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `403 forbidden - does not have the right role`() {
    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", "XXX")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `Get History should return a list with a payload`() {
    val caseloadId = "XYZ"
    val request = createSyncOffenderTransactionRequest(caseloadId)

    val offenderTransaction: SyncTransactionReceipt = webTestClient
      .post()
      .uri("/sync/offender-transactions")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(request)
      .exchange()
      .expectBody(SyncTransactionReceipt::class.java)
      .returnResult()
      .responseBody!!

    webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content").isArray
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].synchronizedTransactionId")
      .isEqualTo(offenderTransaction.synchronizedTransactionId.toString())
      .jsonPath("$.content[0].legacyTransactionId")
      .isEqualTo(request.transactionId.toString())
      .jsonPath("$.content[0].caseloadId")
      .isEqualTo(request.caseloadId)
      .jsonPath("$.content[0].transactionTimestamp").value<String> {
        assertThat(it).startsWith(request.transactionTimestamp.toString().substring(0, 19))
      }
      .jsonPath("$.content[0].requestTypeIdentifier")
      .isEqualTo("SyncOffenderTransactionRequest")
      .jsonPath("$.content[0].requestId").exists()
      .jsonPath("$.content[0].timestamp").exists()
      .jsonPath("$.totalElements").isEqualTo(1)
      .jsonPath("$.totalPages").isEqualTo(1)
      .jsonPath("$.number").isEqualTo(0)
      .jsonPath("$.size").exists()
      .jsonPath("$.first").isEqualTo(true)
      .jsonPath("$.last").isEqualTo(true)
  }

  @Test
  fun `Get History should return a list with multiple payloads`() {
    val caseloadId = "ZZZ"

    val request1 = createSyncOffenderTransactionRequest(caseloadId)
    val request2 = createSyncOffenderTransactionRequest(caseloadId)
    val requests = listOf(request1, request2)

    val offenderTransactions = mutableListOf<SyncTransactionReceipt>()
    for (request in requests) {
      val offenderTransaction: SyncTransactionReceipt = webTestClient
        .post()
        .uri("/sync/offender-transactions")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(request)
        .exchange()
        .expectBody(SyncTransactionReceipt::class.java)
        .returnResult()
        .responseBody!!
      offenderTransactions.add(offenderTransaction)
    }

    val res = webTestClient
      .get()
      .uri("/audit/history?prisonId={prisonId}", caseloadId)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content").isArray
      .jsonPath("$.content.length()").isEqualTo(requests.size)
      .jsonPath("$.totalElements").isEqualTo(requests.size)
      .jsonPath("$.totalPages").isEqualTo(1)
      .jsonPath("$.number").isEqualTo(0)
      .jsonPath("$.size").exists()
      .jsonPath("$.first").isEqualTo(true)
      .jsonPath("$.last").isEqualTo(true)

    for (i in 0..1) {
      res
        .jsonPath("$.content[$i].synchronizedTransactionId")
        .isEqualTo(offenderTransactions[i].synchronizedTransactionId.toString())
        .jsonPath("$.content[$i].legacyTransactionId")
        .isEqualTo(requests[i].transactionId.toString())
        .jsonPath("$.content[$i].caseloadId")
        .isEqualTo(requests[i].caseloadId)
        .jsonPath("$.content[$i].transactionTimestamp").value<String> {
          assertThat(it).startsWith(requests[i].transactionTimestamp.toString().substring(0, 19))
        }
        .jsonPath("$.content[$i].requestTypeIdentifier")
        .isEqualTo("SyncOffenderTransactionRequest")
        .jsonPath("$.content[$i].requestId").exists()
        .jsonPath("$.content[$i].timestamp").exists()
    }
  }
}
