package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.health.GeneralLedgerApiHealthPing
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AdvancesMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.GeneralLedgerTransactionMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.HoldsMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@TestConfiguration
class IntegrationTestHelpers(
  private val jwtAuthHelper: JwtAuthorisationHelper,
  private val generalLedgerTransactionMappingRepository: GeneralLedgerTransactionMappingRepository,
  private val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
  private val holdsMappingRepository: HoldsMappingRepository,
  private val advancesMappingRepository: AdvancesMappingRepository,
  private val generalLedgerApiHealthPing: GeneralLedgerApiHealthPing,
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

  fun stubAndCreateTransaction(
    transactionId: Long,
    prisonNumber: String,
    caseloadId: String,
    transactionType: String = "CANT",
    transactionUUID: UUID = UUID.randomUUID(),
    prisonAccountCode: Int = 1101,
    prisonerAccountCode: Int = 2101,
    prisonAccountRef: String = "CASH",
  ): SyncTransactionReceipt {
    val prisonAccountUUID = UUID.randomUUID()
    val prisonSubAccountUUID = UUID.randomUUID()
    generalLedgerApi.stubGetAccount(
      reference = caseloadId,
      returnUuid = prisonAccountUUID,
      subAccounts = listOf(
        SubAccountResponse(
          id = prisonSubAccountUUID,
          reference = "$prisonAccountCode:$transactionType",
          parentAccountId = prisonAccountUUID,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
      ),
    )

    val prisonerAccountUUID = UUID.randomUUID()
    val prisonerSubAccountUUID = UUID.randomUUID()
    generalLedgerApi.stubGetAccount(
      reference = prisonNumber,
      returnUuid = prisonerAccountUUID,
      subAccounts = listOf(
        SubAccountResponse(
          id = prisonerSubAccountUUID,
          reference = prisonAccountRef,
          parentAccountId = prisonerAccountUUID,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
      ),
    )

    generalLedgerApi.stubPostTransaction(
      returnUUID = transactionUUID,
      amount = 1000,
      legacyTransactionId = transactionId.toString(),
      creditorSubAccountUuid = prisonSubAccountUUID.toString(),
      debtorSubAccountUuid = prisonerSubAccountUUID.toString(),
    )

    val offenderTransactionRequest = SyncOffenderTransactionRequest(
      transactionId = transactionId,
      requestId = UUID.randomUUID(),
      caseloadId = caseloadId,
      transactionTimestamp = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      createdBy = "TestHelper",
      createdByDisplayName = "Test Helper",
      lastModifiedAt = LocalDateTime.now(),
      lastModifiedBy = "",
      lastModifiedByDisplayName = "",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 123123,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 123,
          subAccountType = "REG",
          postingType = "DR",
          type = transactionType,
          description = "TEST",
          amount = BigDecimal.valueOf(1),
          reference = "",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(
              entrySequence = 1,
              code = prisonerAccountCode,
              postingType = "DR",
              amount = BigDecimal.valueOf(1),
            ),
            GeneralLedgerEntry(
              entrySequence = 2,
              code = prisonAccountCode,
              postingType = "CR",
              amount = BigDecimal.valueOf(1),
            ),
          ),
        ),
      ),
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
    holdsMappingRepository.deleteAllInBatch()
    advancesMappingRepository.deleteAllInBatch()
  }
}
