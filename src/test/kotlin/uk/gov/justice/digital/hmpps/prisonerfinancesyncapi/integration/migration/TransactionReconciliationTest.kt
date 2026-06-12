package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.MediaType
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
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.TransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PagedTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
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
  ): Pair<TransactionResponse, List<SearchPostingResponse>> {
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

    return Pair(transactionResponse, postingSearchResponses)
  }

  @Transactional
  @BeforeEach
  fun setup() {
    integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
  }

  @Nested
  inner class ReconcileOffenderTransactionById {
    // At present Syscon only sends one-to-one transactions.
    // IE. CANT transactions are split into multiple one-to-one transactions
    @Test
    fun `should return the general ledger transaction in Syscon format when given the corresponding ID`() {
      val legacyTransactionId = 12345L

      val prisonNumber = "A9971EC"
      val prisonerAccountId: UUID = UUID.randomUUID()
      val creditSubAccountId: UUID = UUID.randomUUID()
      val debtorSubAccountId: UUID = UUID.randomUUID()

      val transactionDate = Instant.now()
      val (glTransactionResponse, postingSearchResponses) = stubPrisonerCashToSpendsTransferResponsesFromGL(
        prisonNumber = prisonNumber,
        parentAccountUUID = prisonerAccountId,
        creditSubAccountUUID = creditSubAccountId,
        debtorSubAccountUUID = debtorSubAccountId,
        transactionDate = transactionDate,
        amount = 500,
      )

      generalLedgerApi.stubSearchTransactionsByUUIDs(
        listOf(glTransactionResponse.id),
        listOf(
          SearchTransactionResponse(
            id = glTransactionResponse.id,
            createdBy = "",
            createdAt = glTransactionResponse.createdAt,
            reference = glTransactionResponse.reference,
            description = glTransactionResponse.description,
            timestamp = glTransactionResponse.timestamp,
            amount = glTransactionResponse.amount,
            entrySequence = 1,
            postings = postingSearchResponses,
          ),
        ),
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
        reference = glTransactionResponse.reference,
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
        .uri("/reconcile/offender-transactions/${glTransactionResponse.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk
        .expectBody<SyncGeneralLedgerTransactionResponse>().returnResult().responseBody!!

      assertThat(transactionResponse.synchronizedTransactionId).isEqualTo(glTransactionResponse.id)
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
    fun `should return 404 when the transaction ID is not found in GL`() {
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

      generalLedgerApi.stubSearchTransactionsByUUIDs(
        emptyList(),
        emptyList(),
      )

      val error = webTestClient
        .get()
        .uri("/reconcile/offender-transactions/${transactionResponse.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isNotFound
        .expectBody<ErrorResponse>().returnResult().responseBody!!

      assertThat(error.developerMessage).isEqualTo("No gl transaction found for gl ${transactionResponse.id}")
    }

    @Test
    fun `should return a 404 when there is no mapping entry found in sync`() {
      val incorrectUUID = UUID.randomUUID()
      val error = webTestClient
        .get()
        .uri("/reconcile/offender-transactions/$incorrectUUID")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isNotFound
        .expectBody<ErrorResponse>().returnResult().responseBody!!

      assertThat(error.developerMessage).isEqualTo("No mapping found for $incorrectUUID")
    }

    @Test
    fun `should return 401 when unauthorized`() {
      val incorrectUUID = UUID.randomUUID()
      webTestClient.get()
        .uri("/reconcile/offender-transactions/$incorrectUUID")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 403 when given an incorrect role`() {
      val incorrectUUID = UUID.randomUUID()
      webTestClient.get()
        .uri("/reconcile/offender-transactions/$incorrectUUID")
        .headers(setAuthorisation(roles = listOf("INCORRECT_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return 502 when the general ledger API returns a 5XX error`() {
      val legacyTransactionId = 12345L

      val prisonNumber = "A9971EC"
      val prisonerAccountId: UUID = UUID.randomUUID()
      val creditSubAccountId: UUID = UUID.randomUUID()
      val debtorSubAccountId: UUID = UUID.randomUUID()

      val transactionDate = Instant.now()
      val (glTransactionResponse, postingSearchResponses) = stubPrisonerCashToSpendsTransferResponsesFromGL(
        prisonNumber = prisonNumber,
        parentAccountUUID = prisonerAccountId,
        creditSubAccountUUID = creditSubAccountId,
        debtorSubAccountUUID = debtorSubAccountId,
        transactionDate = transactionDate,
        amount = 500,
      )

      generalLedgerApi.stubSearchTransactionsByUUIDsThrows500()

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
        reference = glTransactionResponse.reference,
      )

      integrationTestHelpers.syncOffenderTransactions(
        transactionId = legacyTransactionId,
        caseloadId = "LEI",
        transactionTimestamp = LocalDateTime.now(),
        createdAt = LocalDateTime.now(),
        offenderTransactions = listOf(offenderTransaction),
      )

      val error = webTestClient
        .get()
        .uri("/reconcile/offender-transactions/${glTransactionResponse.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().is5xxServerError
        .expectBody<ErrorResponse>().returnResult().responseBody!!

      assertThat(error.status).isEqualTo(502)
    }
  }

  @Nested
  inner class ReconcileOffenderTransactionByDateRange {

    val timeConversion = TimeConversionService()

    @Test
    fun `should return no transactions when no transactions exist for the given date range`() {
      generalLedgerApi.stubSearchTransactionsByUUIDs(emptyList(), emptyList())

      val transactionsResponse = webTestClient
        .get()
        .uri("/reconcile/offender-transactions?startDate=2025-01-01&endDate=2025-01-02")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk
        .expectBody<PagedTransactionResponse>().returnResult().responseBody!!

      assertThat(transactionsResponse.transactions).isEmpty()
    }

    fun createTransactionAndStubGeneralLedger(legacyTransactionId: Long, transactionDateTime: LocalDateTime): Pair<TransactionResponse, List<SearchPostingResponse>> {
      val prisonNumber = "A9971EC"
      val prisonerAccountId: UUID = UUID.randomUUID()
      val creditSubAccountId: UUID = UUID.randomUUID()
      val debtorSubAccountId: UUID = UUID.randomUUID()

      val (glTransaction, postingResponse) = stubPrisonerCashToSpendsTransferResponsesFromGL(
        prisonNumber = prisonNumber,
        parentAccountUUID = prisonerAccountId,
        creditSubAccountUUID = creditSubAccountId,
        debtorSubAccountUUID = debtorSubAccountId,
        transactionDate = timeConversion.toUtcInstant(transactionDateTime),
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
        reference = glTransaction.reference,
      )

      integrationTestHelpers.syncOffenderTransactions(
        transactionId = legacyTransactionId,
        caseloadId = "LEI",
        transactionTimestamp = transactionDateTime,
        createdAt = transactionDateTime,
        offenderTransactions = listOf(offenderTransaction),
      )

      return Pair(glTransaction, postingResponse)
    }

    @Test
    fun `should a paginated response of transactions when transactions exist for the given date range`() {
      val legacyTransactionId = 12345L

      val firstOfJan2025 = LocalDateTime.of(2025, 1, 1, 1, 1)

      val (glTransaction, postingResponse) = createTransactionAndStubGeneralLedger(legacyTransactionId, firstOfJan2025)

      generalLedgerApi.stubSearchTransactionsByUUIDs(
        listOf(glTransaction.id),
        listOf(
          SearchTransactionResponse(
            id = glTransaction.id,
            createdBy = "",
            createdAt = glTransaction.createdAt,
            reference = glTransaction.reference,
            description = glTransaction.description,
            timestamp = glTransaction.timestamp,
            amount = glTransaction.amount,
            entrySequence = 1,
            postings = postingResponse,
          ),
        ),
      )

      val transactionsResponse = webTestClient
        .get()
        .uri("/reconcile/offender-transactions?startDate=2025-01-01&endDate=2025-01-02")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk
        .expectBody<PagedTransactionResponse>().returnResult().responseBody!!

      assertThat(transactionsResponse.transactions).hasSize(1)

      val firstTransaction = transactionsResponse.transactions.first()

      assertThat(firstTransaction.synchronizedTransactionId).isEqualTo(glTransaction.id)
      assertThat(firstTransaction.legacyTransactionId).isEqualTo(legacyTransactionId)
      assertThat(firstTransaction.transactionType).isEqualTo("ATOF")
      assertThat(firstTransaction.description).isEqualTo("Mock Transaction Description")
      assertThat(firstTransaction.generalLedgerEntries.size).isEqualTo(2)
      assertThat(firstTransaction.generalLedgerEntries[0].entrySequence).isEqualTo(1)
      assertThat(firstTransaction.generalLedgerEntries[0].code).isEqualTo(2101)
      assertThat(firstTransaction.generalLedgerEntries[0].postingType).isEqualTo("CR")
      assertThat(firstTransaction.generalLedgerEntries[0].amount).isEqualTo(BigDecimal("5.00"))

      assertThat(firstTransaction.generalLedgerEntries[1].entrySequence).isEqualTo(2)
      assertThat(firstTransaction.generalLedgerEntries[1].code).isEqualTo(2102)
      assertThat(firstTransaction.generalLedgerEntries[1].postingType).isEqualTo("DR")
      assertThat(firstTransaction.generalLedgerEntries[1].amount).isEqualTo(BigDecimal("5.00"))
    }

    @Test
    fun `should return 400 when page requested is out of range`() {
      generalLedgerApi.stubSearchTransactionThrowsOutOfBoundsException()

      val response = webTestClient
        .get()
        .uri("/reconcile/offender-transactions?startDate=2025-01-01&endDate=2025-01-02")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ErrorResponse>().returnResult().responseBody!!

      assertThat(response.userMessage).isEqualTo("Page requested is out of range")
    }

    @Test
    fun `should return 400 when startDate is invalid`() {
      webTestClient
        .get()
        .uri("/reconcile/offender-transactions?startDate=invalid&endDate=2025-01-02")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ErrorResponse>().returnResult().responseBody!!
    }

    @Test
    fun `should return 400 when endDate is invalid`() {
      webTestClient
        .get()
        .uri("/reconcile/offender-transactions?startDate=2025-01-01&endDate=invalid")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ErrorResponse>().returnResult().responseBody!!
    }

    @Test
    fun `should return 400 when startDate is missing`() {
      webTestClient
        .get()
        .uri("/reconcile/offender-transactions?endDate=2025-01-02")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ErrorResponse>().returnResult().responseBody!!
    }

    @Test
    fun `should return 400 when endDate is missing`() {
      webTestClient
        .get()
        .uri("/reconcile/offender-transactions?startDate=2025-01-02")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ErrorResponse>().returnResult().responseBody!!
    }

    @ParameterizedTest
    @CsvSource("0", "-1", "abc")
    fun `should return 400 when page number is invalid`(inputPageNumber: String) {
      webTestClient
        .get()
        .uri("/reconcile/offender-transactions?startDate=2025-01-01&endDate=2025-01-02&pageNumber=$inputPageNumber")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ErrorResponse>().returnResult().responseBody!!
    }

    @ParameterizedTest
    @CsvSource("0", "-1", "abc")
    fun `should return 400 when page size is invalid`(inputPageSize: String) {
      webTestClient
        .get()
        .uri("/reconcile/offender-transactions?startDate=2025-01-01&endDate=2025-01-02&pageSize=$inputPageSize")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ErrorResponse>().returnResult().responseBody!!
    }

    @Test
    fun `401 unauthorised`() {
      webTestClient
        .get()
        .uri("/reconcile/offender-transactions?startDate=2025-01-01&endDate=2025-01-02")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `403 forbidden - does not have the right role`() {
      webTestClient
        .get()
        .uri("/reconcile/offender-transactions?startDate=2025-01-01&endDate=2025-01-02")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("SOME_OTHER_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return 502 when the general ledger API returns a 5XX error`() {
      generalLedgerApi.stubSearchTransactionsByUUIDsThrows500()

      val error = webTestClient
        .get()
        .uri("/reconcile/offender-transactions?startDate=2025-01-01&endDate=2025-01-02")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().is5xxServerError
        .expectBody<ErrorResponse>().returnResult().responseBody!!

      assertThat(error.status).isEqualTo(502)
    }
  }
}
