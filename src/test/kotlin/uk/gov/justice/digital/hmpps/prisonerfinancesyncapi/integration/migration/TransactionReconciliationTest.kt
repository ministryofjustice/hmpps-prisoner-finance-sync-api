package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SearchPostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SearchTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@TestPropertySource(
  properties = [
    "feature.general-ledger-api.enabled=true",
    "feature.general-ledger-api.test-prisoner-ids=A9971EC",
  ],
)
@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class TransactionReconciliationTest : IntegrationTestBase() {

  private fun stubPrisonerCashToSpendsTransferResponsesFromGL(
    prisonNumber: String,
    parentAccountUUID: UUID,
    creditSubAccountUUID: UUID,
    debtorSubAccountUUID: UUID,
    transactionDate: Instant,
    amount: Long,
  ): SearchTransactionResponse? {
    val subAccountOneRef = "CASH"
    val subAccountTwoRef = "SPENDS"

    val subAccountsRefs = listOf(subAccountOneRef, subAccountTwoRef)

    generalLedgerApi.stubGetAccount(
      reference = prisonNumber,
      subAccounts = listOf(
        SubAccountResponse(
          id = debtorSubAccountUUID,
          reference = subAccountOneRef,
          parentAccountId = parentAccountUUID,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = creditSubAccountUUID,
          reference = subAccountTwoRef,
          parentAccountId = parentAccountUUID,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
      ),
    )

    val transactionPostings = listOf(
      PostingResponse(
        id = parentAccountUUID,
        createdBy = "TEST",
        createdAt = transactionDate,
        type = PostingResponse.Type.CR,
        amount = amount,
        subAccountID = creditSubAccountUUID,
      ),
      PostingResponse(
        id = parentAccountUUID,
        createdBy = "TEST",
        createdAt = transactionDate,
        type = PostingResponse.Type.DR,
        amount = amount,
        subAccountID = debtorSubAccountUUID,
      ),
    )

    val returnGeneralLedgerUUID = UUID.randomUUID()

    val transactionResponse = generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = creditSubAccountUUID.toString(),
      debtorSubAccountUuid = debtorSubAccountUUID.toString(),
      reference = "REF",
      returnUUID = returnGeneralLedgerUUID,
      postings = transactionPostings,
      amount = amount,
    )

    val postingSearchResponses = transactionResponse.postings.withIndex().map { (index, it) ->
      SearchPostingResponse(
        id = it.id,
        createdBy = it.createdBy,
        createdAt = it.createdAt,
        type = SearchPostingResponse.Type.valueOf(it.type.name),
        amount = it.amount,
        subAccountID = it.subAccountID,
        subAccountReference = subAccountsRefs[index],
        accountID = parentAccountUUID,
        accountReference = prisonNumber,
        entrySequence = index.toLong() + 1,
      )
    }

    val glTransactionResponse = generalLedgerApi.stubSearchTransactionsByUUIDs(
      listOf(transactionResponse.id),
      listOf(
        SearchTransactionResponse(
          id = transactionResponse.id,
          createdBy = "",
          createdAt = transactionResponse.createdAt,
          reference = transactionResponse.reference,
          description = transactionResponse.description,
          timestamp = transactionResponse.timestamp,
          amount = transactionResponse.amount,
          entrySequence = 1,
          postings = postingSearchResponses,
        ),
      ),
    )

    return glTransactionResponse.firstOrNull()
  }

  @Transactional
  @BeforeEach
  fun setup() {
    integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
  }

  // At present Syscon only sends one-to-one transactions.
  // IE. CANT transactions are split into multiple one-to-one transactions
  @Test
  fun `Should return the general ledger transaction in Syscon format when given the corresponding ID`() {
    val legacyTransactionId = 12345L

    val prisonNumber = "A9971EC"
    val prisonerAccountId: UUID = UUID.randomUUID()
    val creditSubAccountId: UUID = UUID.randomUUID()
    val debtorSubAccountId: UUID = UUID.randomUUID()

    val transactionDate = Instant.now()
    val glTransaction = stubPrisonerCashToSpendsTransferResponsesFromGL(
      prisonNumber = prisonNumber,
      parentAccountUUID = prisonerAccountId,
      creditSubAccountUUID = creditSubAccountId,
      debtorSubAccountUUID = debtorSubAccountId,
      transactionDate = transactionDate,
      amount = 500,
    )

    val generalLedgerEntries = listOf(
      GeneralLedgerEntry(
        entrySequence = 1,
        code = 2101,
        postingType = "DR",
        amount = BigDecimal.valueOf(500),
      ),
      GeneralLedgerEntry(
        entrySequence = 2,
        code = 2102,
        postingType = "CR",
        amount = BigDecimal.valueOf(500),
      ),
    )

    val offenderTransaction = integrationTestHelpers.createOffenderTransaction(
      entrySequence = 1,
      offenderId = 1,
      offenderDisplayId = prisonNumber,
      offenderBookingId = 1,
      subAccountType = "",
      amount = BigDecimal.valueOf(5.00),
      generalLedgerEntries = generalLedgerEntries,
      reference = glTransaction!!.reference,
    )

    integrationTestHelpers.syncOffenderTransactions(
      transactionId = legacyTransactionId,
      caseloadId = "LEI",
      transactionTimestamp = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      offenderTransactions = listOf(offenderTransaction),
    )

    val transactionResponse = webTestClient
      .get()
      .uri("/reconcile/offender-transactions/${glTransaction.id}")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody<SyncGeneralLedgerTransactionResponse>().returnResult().responseBody!!

    assertThat(transactionResponse.synchronizedTransactionId).isEqualTo(glTransaction.id)
    assertThat(transactionResponse.legacyTransactionId).isEqualTo(legacyTransactionId)
    assertThat(transactionResponse.transactionType).isEqualTo("ATOF")
    assertThat(transactionResponse.description).isEqualTo("Mock Transaction Description")
    assertThat(transactionResponse.generalLedgerEntries.size).isEqualTo(2)
    assertThat(transactionResponse.generalLedgerEntries[0].entrySequence).isEqualTo(1)
    assertThat(transactionResponse.generalLedgerEntries[0].code).isEqualTo(2101)
    assertThat(transactionResponse.generalLedgerEntries[0].postingType).isEqualTo("CR")
    assertThat(transactionResponse.generalLedgerEntries[0].amount).isEqualTo(BigDecimal("5.00"))

    assertThat(transactionResponse.generalLedgerEntries[1].entrySequence).isEqualTo(2)
    assertThat(transactionResponse.generalLedgerEntries[1].code).isEqualTo(2102)
    assertThat(transactionResponse.generalLedgerEntries[1].postingType).isEqualTo("DR")
    assertThat(transactionResponse.generalLedgerEntries[1].amount).isEqualTo(BigDecimal("5.00"))
  }

  @Test
  fun `Should return 404 when the transaction ID is not found in GL`() {
    val legacyTransactionId = 12345L

    val prisonNumber = "A9971EC"
    val prisonerAccountUUID: UUID = UUID.randomUUID()
    val creditSubAccountUUID: UUID = UUID.randomUUID()
    val debtorSubAccountUUID: UUID = UUID.randomUUID()

    generalLedgerApi.stubGetAccount(
      reference = prisonNumber,
      subAccounts = listOf(
        SubAccountResponse(
          id = debtorSubAccountUUID,
          reference = "CASH",
          parentAccountId = prisonerAccountUUID,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = creditSubAccountUUID,
          reference = "SPENDS",
          parentAccountId = prisonerAccountUUID,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
      ),
    )

    val transactionPostings = listOf(
      PostingResponse(
        id = prisonerAccountUUID,
        createdBy = "TEST",
        createdAt = Instant.now(),
        type = PostingResponse.Type.CR,
        amount = 6,
        subAccountID = creditSubAccountUUID,
      ),
      PostingResponse(
        id = prisonerAccountUUID,
        createdBy = "TEST",
        createdAt = Instant.now(),
        type = PostingResponse.Type.DR,
        amount = 6,
        subAccountID = debtorSubAccountUUID,
      ),
    )

    val returnGeneralLedgerUUID = UUID.randomUUID()

    val transactionResponse = generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = creditSubAccountUUID.toString(),
      debtorSubAccountUuid = debtorSubAccountUUID.toString(),
      reference = "REF",
      returnUUID = returnGeneralLedgerUUID,
      postings = transactionPostings,
      amount = 6,
    )

    val generalLedgerEntries = listOf(
      GeneralLedgerEntry(
        entrySequence = 1,
        code = 2101,
        postingType = "DR",
        amount = BigDecimal.valueOf(500),
      ),
      GeneralLedgerEntry(
        entrySequence = 2,
        code = 2102,
        postingType = "CR",
        amount = BigDecimal.valueOf(500),
      ),
    )

    val offenderTransaction = integrationTestHelpers.createOffenderTransaction(
      entrySequence = 1,
      offenderId = 1,
      offenderDisplayId = prisonNumber,
      offenderBookingId = 1,
      subAccountType = "",
      amount = BigDecimal.valueOf(5.00),
      generalLedgerEntries = generalLedgerEntries,
      reference = "ANY_REF",
    )

    integrationTestHelpers.syncOffenderTransactions(
      transactionId = legacyTransactionId,
      caseloadId = "LEI",
      transactionTimestamp = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      offenderTransactions = listOf(offenderTransaction),
    )

    generalLedgerApi.stubSearchTransactionsByUUIDs(emptyList(), emptyList())

    val error = webTestClient
      .get()
      .uri("/reconcile/offender-transactions/${transactionResponse.id}")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isNotFound
      .expectBody<ErrorResponse>().returnResult().responseBody!!

    assertThat(error.developerMessage).isEqualTo("No gl transaction found for gl ${transactionResponse.id}")
  }
}
