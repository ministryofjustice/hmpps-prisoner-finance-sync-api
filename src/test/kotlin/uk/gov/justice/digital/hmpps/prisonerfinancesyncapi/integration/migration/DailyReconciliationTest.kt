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
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.verify.DailyReconciliationResponse
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
class DailyReconciliationTest : IntegrationTestBase() {

  @Transactional
  @BeforeEach
  fun setup() {
    integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
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

  @Test
  fun `should return 401 when unauthorized`() {
    webTestClient
      .get()
      .uri("/verify/offender-transactions/2026-01-01")
      .exchange()
      .expectStatus().isUnauthorized
  }

  private fun stubPrisonerCASHtoSPENDSXferResponsesFromGL(
    prisonNumber: String,
    parentAccountUUID: UUID,
    creditSubAccountUUID: UUID,
    debtorSubAccountUUID: UUID,
    transactionDate: Instant,
  ): UUID {
    generalLedgerApi.stubGetAccount(
      reference = prisonNumber,
      subAccounts = listOf(
        SubAccountResponse(
          id = debtorSubAccountUUID,
          reference = "CASH",
          parentAccountId = parentAccountUUID,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = creditSubAccountUUID,
          reference = "SPENDS",
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

    generalLedgerApi.stubGetTransactionByUUID(
      transactionUUID = returnGeneralLedgerUUID,
      reference = "REF",
      createdAt = transactionDate,
      timeStamp = transactionDate,
      amount = 0,
      postings = transactionPostings,
    )

    // Return UUID here is put into the Nomis to GL map
    generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = creditSubAccountUUID.toString(),
      debtorSubAccountUuid = debtorSubAccountUUID.toString(),
      reference = "REF",
      returnUUID = returnGeneralLedgerUUID,
    )

    return returnGeneralLedgerUUID
  }

  @Test
  fun `should return a 200 response an empty list if no transactions on the given date`() {
    val baseDate = LocalDate.of(2025, 5, 21)
    val createdAt = baseDate.atStartOfDay()
    val syncOffenderTransactionDate = baseDate.atStartOfDay().toInstant(ZoneOffset.UTC)

    val prisonNumber = "A9971EC"

    val prisonerParentAccountUUID = UUID.randomUUID()
    val creditSubAccountUUID = UUID.randomUUID()
    val debtorSubAccountUUID = UUID.randomUUID()

    stubPrisonerCASHtoSPENDSXferResponsesFromGL(
      prisonNumber = prisonNumber,
      parentAccountUUID = prisonerParentAccountUUID,
      creditSubAccountUUID = creditSubAccountUUID,
      debtorSubAccountUUID = debtorSubAccountUUID,
      transactionDate = syncOffenderTransactionDate,
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
      .expectBody<DailyReconciliationResponse>().returnResult().responseBody!!

    assertThat(dailyReconciliationResponse.transactions.size).isEqualTo(0)
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

    val glUUIDs = mutableListOf<UUID>()

    repeat(3) {
      val generalLedgerUUID = stubPrisonerCASHtoSPENDSXferResponsesFromGL(
        prisonNumber = prisonNumber,
        parentAccountUUID = prisonerParentAccountUUID,
        creditSubAccountUUID = creditSubAccountUUID,
        debtorSubAccountUUID = debtorSubAccountUUID,
        transactionDate = syncOffenderTransactionDate,
      )
      glUUIDs.add(generalLedgerUUID)

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
        Random.nextLong(),
        "LEI",
        createdAt,
        createdAt,
        offenderTransactions = listOf(offenderTransactions),
      )
    }

    val dailyReconciliationResponse = webTestClient
      .get()
      .uri("/verify/offender-transactions/$stringDate")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody<DailyReconciliationResponse>().returnResult().responseBody!!

    assertThat(dailyReconciliationResponse.transactions.size).isEqualTo(3)

    for (i in 0 until glUUIDs.size) {
      val glUUID = glUUIDs[i]
      assertThat(dailyReconciliationResponse.transactions[i].glTransactionId).isEqualTo(glUUID)
    }

    assertThat(dailyReconciliationResponse.transactions[0].postings.size).isEqualTo(2)
  }
}
