package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SearchPostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SearchTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.verify.TransactionReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

@TestPropertySource(
  properties = [
    "feature.general-ledger-api.enabled=true",
    "feature.general-ledger-api.test-prisoner-ids=A9971EC",
  ],
)
@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class DailyReconciliationTest(@Autowired private val timeConversionService: TimeConversionService) : IntegrationTestBase() {

  @Transactional
  @BeforeEach
  fun setup() {
    integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
  }

  private fun stubPrisonerCashToSpendsTransferResponsesFromGL(
    prisonNumber: String,
    parentAccountUUID: UUID,
    creditSubAccountUUID: UUID,
    debtorSubAccountUUID: UUID,
    transactionDate: Instant,
  ): SearchTransactionResponse {
    val subAccountOneRef = "CASH"
    val subAccountTwoRef = "SPENDS"

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
        amount = 0,
        subAccountID = creditSubAccountUUID,
      ),
      PostingResponse(
        id = parentAccountUUID,
        createdBy = "TEST",
        createdAt = transactionDate,
        type = PostingResponse.Type.DR,
        amount = 0,
        subAccountID = debtorSubAccountUUID,
      ),
    )

    val returnGeneralLedgerUUID = UUID.randomUUID()

    // Return UUID here is put into the Nomis to GL map
    val transactionResponse = generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = creditSubAccountUUID.toString(),
      debtorSubAccountUuid = debtorSubAccountUUID.toString(),
      reference = "REF",
      returnUUID = returnGeneralLedgerUUID,
      postings = transactionPostings,
    )

    val searchPostingResponses = transactionResponse.postings.withIndex().map { (index, posting) ->
      SearchPostingResponse(
        id = posting.id,
        createdBy = posting.createdBy,
        createdAt = posting.createdAt,
        type = SearchPostingResponse.Type.valueOf(posting.type.name),
        amount = posting.amount,
        subAccountID = posting.subAccountID,
        subAccountReference = if (index == 0) subAccountOneRef else subAccountTwoRef,
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
          postings = searchPostingResponses,
        ),
      ),
    )

    return glTransactionResponse.content.first()
  }

  @Test
  fun `should return a 200 response an empty list if no transactions on the given date`() {
    val baseDate = LocalDate.of(2025, 5, 21)
    val createdAt = baseDate.atStartOfDay()
    val syncOffenderTransactionDate = timeConversionService.toUtcInstant(createdAt)

    val prisonNumber = "A9971EC"

    val prisonerParentAccountUUID = UUID.randomUUID()
    val creditSubAccountUUID = UUID.randomUUID()
    val debtorSubAccountUUID = UUID.randomUUID()

    stubPrisonerCashToSpendsTransferResponsesFromGL(
      prisonNumber = prisonNumber,
      parentAccountUUID = prisonerParentAccountUUID,
      creditSubAccountUUID = creditSubAccountUUID,
      debtorSubAccountUUID = debtorSubAccountUUID,
      transactionDate = syncOffenderTransactionDate,
    )

    generalLedgerApi.stubSearchTransactionsByUUIDs(emptyList(), emptyList())

    val generalLedgerEntries = listOf(
      GeneralLedgerEntry(
        entrySequence = 1,
        code = 2101,
        postingType = "DR",
        amount = BigDecimal.valueOf(1),
      ),
      GeneralLedgerEntry(
        entrySequence = 2,
        code = 2102,
        postingType = "CR",
        amount = BigDecimal.valueOf(1),
      ),
    )

    val offenderTransactions = integrationTestHelpers.createOffenderTransaction(
      entrySequence = 1,
      offenderId = 123456,
      offenderDisplayId = "A9971EC",
      offenderBookingId = 12345678,
      subAccountType = "REG",
      amount = BigDecimal.valueOf(1),
      generalLedgerEntries = generalLedgerEntries,
      reference = "REF",
    )

    integrationTestHelpers.syncOffenderTransactions(
      1,
      "LEI",
      createdAt,
      createdAt,
      offenderTransactions = listOf(offenderTransactions),
    )

    val dailyReconciliationResponse = webTestClient
      .get()
      .uri("/verify/offender-transactions/1900-01-01")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody<PagedResponse<TransactionReconciliationResponse>>().returnResult().responseBody!!

    assertThat(dailyReconciliationResponse.content.size).isEqualTo(0)
  }

  @Test
  fun `should return a 200 response and transactions from a given date`() {
    val baseDate = LocalDate.of(2025, 5, 21)
    val createdAt = baseDate.atStartOfDay()
    val syncOffenderTransactionDate = baseDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val stringDate = baseDate.format(dateFormatter)

    val prisonNumber = "A9971EC"

    val prisonerParentAccountUUID = UUID.randomUUID()
    val creditSubAccountUUID = UUID.randomUUID()
    val debtorSubAccountUUID = UUID.randomUUID()

    val glIdAndNomisIdPairs = mutableListOf<Pair<UUID, Long>>()

    val glTransactions = mutableListOf<SearchTransactionResponse>()

    repeat(3) {
      val transactionResponse = stubPrisonerCashToSpendsTransferResponsesFromGL(
        prisonNumber = prisonNumber,
        parentAccountUUID = prisonerParentAccountUUID,
        creditSubAccountUUID = creditSubAccountUUID,
        debtorSubAccountUUID = debtorSubAccountUUID,
        transactionDate = syncOffenderTransactionDate,
      )

      glTransactions.add(transactionResponse)

      val generalLedgerEntries = listOf(
        GeneralLedgerEntry(
          entrySequence = 1,
          code = 2101,
          postingType = "DR",
          amount = BigDecimal.valueOf(1),
        ),
        GeneralLedgerEntry(
          entrySequence = 2,
          code = 2102,
          postingType = "CR",
          amount = BigDecimal.valueOf(1),
        ),
      )

      val offenderTransactions = integrationTestHelpers.createOffenderTransaction(
        entrySequence = 1,
        offenderId = 123456,
        offenderDisplayId = "A9971EC",
        offenderBookingId = 12345678,
        subAccountType = "REG",
        amount = BigDecimal.valueOf(1),
        generalLedgerEntries = generalLedgerEntries,
        reference = "REF",
      )

      val nomisID = Random.nextLong()
      glIdAndNomisIdPairs.add(Pair(transactionResponse.id, nomisID))

      integrationTestHelpers.syncOffenderTransactions(
        nomisID,
        "LEI",
        createdAt,
        createdAt,
        offenderTransactions = listOf(offenderTransactions),
      )
    }

    val glUUIDs = glIdAndNomisIdPairs.map { it.first }

    generalLedgerApi.stubSearchTransactionsByUUIDs(glUUIDs, glTransactions)

    val dailyReconciliationResponse = webTestClient
      .get()
      .uri("/verify/offender-transactions/$stringDate")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody<PagedResponse<TransactionReconciliationResponse>>().returnResult().responseBody!!

    assertThat(dailyReconciliationResponse.content.size).isEqualTo(3)

    val idsInReconciliation = dailyReconciliationResponse.content.map { Pair(it.glTransactionId, it.nomisTransactionId) }.toSet()

    glIdAndNomisIdPairs.forEach { pair -> assertThat(pair in idsInReconciliation).isTrue() }

    assertThat(dailyReconciliationResponse.content[0].postings.size).isEqualTo(2)
  }

  @Test
  fun `should not fail when missing GL entries`() {
    val baseDate = LocalDate.of(2025, 5, 21)
    val createdAt = baseDate.atStartOfDay()
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val stringDate = baseDate.format(dateFormatter)

    val transactionUUID = UUID.randomUUID()

    generalLedgerApi.stubSearchTransactionsByUUIDs(listOf(transactionUUID), emptyList())

    val generalLedgerEntries = listOf(
      GeneralLedgerEntry(
        entrySequence = 1,
        code = 2101,
        postingType = "DR",
        amount = BigDecimal.valueOf(1),
      ),
      GeneralLedgerEntry(
        entrySequence = 2,
        code = 2102,
        postingType = "CR",
        amount = BigDecimal.valueOf(1),
      ),
    )

    val offenderTransactions = integrationTestHelpers.createOffenderTransaction(
      entrySequence = 1,
      offenderId = 123456,
      offenderDisplayId = "A9971EC",
      offenderBookingId = 12345678,
      subAccountType = "REG",
      amount = BigDecimal.valueOf(1),
      generalLedgerEntries = generalLedgerEntries,
      reference = "REF",
    )

    integrationTestHelpers.syncOffenderTransactions(
      1,
      "LEI",
      createdAt,
      createdAt,
      offenderTransactions = listOf(offenderTransactions),
    )

    val dailyReconciliationResponse = webTestClient
      .get()
      .uri("/verify/offender-transactions/$stringDate")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody<PagedResponse<TransactionReconciliationResponse>>().returnResult().responseBody!!

    assertThat(dailyReconciliationResponse.content.size).isEqualTo(0)
  }

  @Test
  fun `should return 401 when unauthorized`() {
    webTestClient
      .get()
      .uri("/verify/offender-transactions/2026-01-01")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @ParameterizedTest
  @CsvSource(
    "0, asd, -1",
  )
  fun `Should return 400 when pageNumber is invalid`(invalidInput: String) {
    webTestClient.get()
      .uri("/verify/offender-transactions/2026-01-01?pageNumber=$invalidInput")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody<ErrorResponse>()
  }

  @ParameterizedTest
  @CsvSource(
    "0, asd, -1",
  )
  fun `Should return 400 when pageSize is invalid`(invalidInput: String) {
    webTestClient.get()
      .uri("/verify/offender-transactions/2026-01-01?pageSize=$invalidInput")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody<ErrorResponse>()
  }

  @Test
  fun `should throw 400 Bad request when date is formated incorrectly`() {
    webTestClient
      .get()
      .uri("/verify/offender-transactions/21-05-2026")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `should return 403 when requesting account with incorrect role`() {
    webTestClient
      .get()
      .uri("/verify/offender-transactions/2026-01-01")
      .headers(setAuthorisation(roles = emptyList()))
      .exchange()
      .expectStatus().isForbidden
  }
}
