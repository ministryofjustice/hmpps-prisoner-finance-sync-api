package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.GeneralLedgerTransactionMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.verify.DailyReconciliationResponse
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class DailyReconciliationTest : IntegrationTestBase() {

  @Transactional
  @BeforeEach
  fun setup() {
    integrationTestHelpers.clearDB()
  }

  @Test
  fun `should return a 200 response and an empty transactions list on a day with no transactions`() {
    webTestClient
      .get()
      .uri("/verify/offender-transactions/2026-05-21")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody<DailyReconciliationResponse>().returnResult().responseBody!!

    // this should return an empty list

    // what happens if they send us a date, we have info from sync but not GL eg we have it in mapping but not in GL?
    // / maybe raise an appinsights, dont send it back just fail. If there are single GL transactions that dont exist we cant send back empty

    //
  }

  @Test
  fun `should return a 200 response and a single transaction from a given date`() {
    val baseDate = LocalDate.of(2025, 5, 21)

    val createdAt = baseDate.atStartOfDay()

    val instantDate = baseDate.atStartOfDay().toInstant(ZoneOffset.UTC)

    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val stringDate = baseDate.format(dateFormatter)

    val prisonNumber = "A9971EC"

    print("$createdAt $stringDate")

    val transactionUUID = UUID.randomUUID()


    generalLedgerApi.stubGetAccount(reference = prisonNumber, subAccounts = listOf(
      SubAccountResponse(
        id = UUID.randomUUID(), reference = "sub-account-2101-ref",
        parentAccountId = UUID.randomUUID(),
        createdBy = "TEST",
        createdAt = Instant.now(),
      ),
      SubAccountResponse(
        id = UUID.randomUUID(), reference = "sub-account-2102-ref",
        parentAccountId = UUID.randomUUID(),
        createdBy = "TEST",
        createdAt = Instant.now(),
      ),
    ))

    generalLedgerApi.stubGetAccount(reference = "LEI", subAccounts = listOf(
      SubAccountResponse(
        id = UUID.randomUUID(), reference = "sub-account-2101-ref",
        parentAccountId = UUID.randomUUID(),
        createdBy = "TEST",
        createdAt = Instant.now(),
      ),
      SubAccountResponse(
        id = UUID.randomUUID(), reference = "sub-account-2102-ref",
        parentAccountId = UUID.randomUUID(),
        createdBy = "TEST",
        createdAt = Instant.now(),
      ),
    ))


    generalLedgerApi.stubGetTransactionByUUID(
      transactionUUID = transactionUUID,
      reference = "reference",
      createdAt = instantDate,
      timeStamp = instantDate,
      amount = 0,
      postings = listOf(
        PostingResponse(
          id = UUID.randomUUID(),
          createdBy = "TEST",
          createdAt = instantDate,
          type = PostingResponse.Type.CR,
          amount = 0,
          subAccountID = UUID.randomUUID(),
        ),
        PostingResponse(
          id = UUID.randomUUID(),
          createdBy = "TEST",
          createdAt = instantDate,
          type = PostingResponse.Type.DR,
          amount = 0,
          subAccountID = UUID.randomUUID(),
        ),
      ),
    )

    generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = "uuid",
      debtorSubAccountUuid = "uuid",
      reference = "reference",
      returnUUID = UUID.randomUUID()
    )

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
      reference = "reference"
    )

    val syncResponse = integrationTestHelpers.syncOffenderTransactions(
      1,
      "LEI",
      createdAt,
      createdAt,
      offenderTransactions = listOf(offenderTransactions),
    )

    Thread.sleep(3000)

    print("$syncResponse")

    val dailyReconciliationResponse = webTestClient
      .get()
      .uri("/verify/offender-transactions/$stringDate")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody<DailyReconciliationResponse>().returnResult().responseBody!!

    print(dailyReconciliationResponse)
    assertThat(dailyReconciliationResponse.transactions.size).isEqualTo(1)
    assertThat(dailyReconciliationResponse.transactions[0].glTransactionId).isEqualTo(transactionUUID)
    assertThat(dailyReconciliationResponse.transactions[0].postings.size).isEqualTo(2)
  }

  // test for multiple transactions being returned , at some point we will need a non - batch get

  // test for multiple transactions being returned , at some point we will need a batch get ???
}
