package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.GeneralLedgerTransactionMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@TestConfiguration
class IntegrationTestHelpers(
  private val jwtAuthHelper: JwtAuthorisationHelper,
  private val generalLedgerTransactionMappingRepository: GeneralLedgerTransactionMappingRepository,
  private val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
) {

  lateinit var webTestClient: WebTestClient

  fun setWebClient(webClient: WebTestClient) {
    webTestClient = webClient
  }

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  fun createOffenderTransaction(
    entrySequence: Int,
    offenderId: Long,
    offenderDisplayId: String,
    offenderBookingId: Long,
    subAccountType: String,
    amount: BigDecimal,
    reference: String,
    generalLedgerEntries: List<GeneralLedgerEntry>,
  ): OffenderTransaction {
    val offenderTransaction = OffenderTransaction(
      entrySequence = entrySequence,
      offenderId = offenderId,
      offenderDisplayId = offenderDisplayId,
      offenderBookingId = offenderBookingId,
      subAccountType = subAccountType,
      postingType = "DR",
      type = "ATOF",
      description = "some transaction",
      amount = amount,
      reference = reference,
      generalLedgerEntries = generalLedgerEntries,
    )

    return offenderTransaction
  }

  fun syncOffenderTransactions(
    transactionId: Long,
    caseloadId: String,
    transactionTimestamp: LocalDateTime,
    createdAt: LocalDateTime,
    offenderTransactions: List<OffenderTransaction>,
  ): SyncTransactionReceipt {
    val offenderTransactionRequest = SyncOffenderTransactionRequest(
      transactionId = transactionId,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      transactionTimestamp = transactionTimestamp,
      createdAt = createdAt,
      createdBy = "TestHelper",
      createdByDisplayName = "Test Helper",
      lastModifiedAt = createdAt,
      lastModifiedBy = "",
      lastModifiedByDisplayName = "",
      offenderTransactions = offenderTransactions,
    )

    val transactionReceipt = webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(offenderTransactionRequest)
      .exchange()
      .expectStatus().isCreated
      .expectBody<SyncTransactionReceipt>()
      .returnResult()
      .responseBody!!

    return transactionReceipt
  }

  fun clearDB() {
    generalLedgerTransactionMappingRepository.deleteAllInBatch()
    nomisSyncPayloadRepository.deleteAllInBatch()
  }
}
