package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.sync

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.EntityExchangeResult
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.time.LocalDateTime
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.collections.List

class DuplicatePrisonerAccountTest : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Test
  fun `should not create duplicate prisoner accounts in race-condition and transfer transaction`() {
    val testOffenderId = 123L
    val transferReq1 = createSyncOffenderTransactionRequest(
      caseloadId = "MDI",
      offenderTransactions = listOf(
        createMockOffenderTransaction(
          type = "TOR",
          amount = 0.00,
          offenderId = testOffenderId,
          postingType = "DR",
          subaccountType = "REG",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = 0.00),
            GeneralLedgerEntry(entrySequence = 2, code = 1101, postingType = "CR", amount = 0.00),
          ),
        ),
        createMockOffenderTransaction(
          type = "TOR",
          amount = 324.00,
          offenderId = testOffenderId,
          postingType = "DR",
          subaccountType = "SPND",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 3, code = 2102, postingType = "DR", amount = 324.00),
            GeneralLedgerEntry(entrySequence = 4, code = 1101, postingType = "CR", amount = 324.00),
          ),
        ),
        createMockOffenderTransaction(
          type = "TOR",
          amount = 0.00,
          offenderId = testOffenderId,
          postingType = "DR",
          subaccountType = "SAV",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 5, code = 2103, postingType = "DR", amount = 0.00),
            GeneralLedgerEntry(entrySequence = 6, code = 1101, postingType = "CR", amount = 0.00),
          ),
        ),
      ),
    )

    val transferReq2 = createSyncOffenderTransactionRequest(
      caseloadId = "KMI",
      offenderTransactions = listOf(
        createMockOffenderTransaction(
          type = "TIR",
          amount = 0.00,
          offenderId = testOffenderId,
          postingType = "CR",
          subaccountType = "REG",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = 1101, postingType = "DR", amount = 0.00),
            GeneralLedgerEntry(entrySequence = 2, code = 2101, postingType = "CR", amount = 0.00),
          ),
        ),
        createMockOffenderTransaction(
          type = "TOR",
          amount = 324.00,
          offenderId = testOffenderId,
          postingType = "DR",
          subaccountType = "SPND",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 3, code = 1101, postingType = "DR", amount = 324.00),
            GeneralLedgerEntry(entrySequence = 4, code = 2102, postingType = "CR", amount = 324.00),
          ),
        ),
        createMockOffenderTransaction(
          type = "TOR",
          amount = 0.00,
          offenderId = testOffenderId,
          postingType = "DR",
          subaccountType = "SAV",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 5, code = 1101, postingType = "DR", amount = 0.00),
            GeneralLedgerEntry(entrySequence = 6, code = 2103, postingType = "CR", amount = 0.00),
          ),
        ),
      ),
    )

    val latch = CountDownLatch(1)
    val results = Collections.synchronizedList(
      mutableListOf<EntityExchangeResult<ByteArray>>(),
    )

    fun fire(request: Any) = Runnable {
      latch.await()

      val result = webTestClient
        .post()
        .uri("/sync/offender-transactions")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated
        .expectBody()
        .returnResult()

      results += result
    }

    val t1 = Thread(fire(transferReq1))
    val t2 = Thread(fire(transferReq2))

    t1.start()
    t2.start()
    latch.countDown()

    t1.join()
    t2.join()

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts", "AA001AA")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[*].code")
      .value<List<Int>> { codes ->
        val duplicates = codes.groupBy { it }.filter { it.value.size > 1 }
        assert(duplicates.isEmpty()) { "Duplicate account codes found: $duplicates" }
      }
  }

  private fun createMockOffenderTransaction(
    type: String = "OT",
    subaccountType: String = "REG",
    postingType: String = "DR",
    amount: Double = 162.00,
    offenderId: Long = 1015388L,
    generalLedgerEntries: List<GeneralLedgerEntry> = listOf(
      GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = 162.00),
      GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = 162.00),
    ),
  ): OffenderTransaction = OffenderTransaction(
    entrySequence = 1,
    offenderId = offenderId,
    offenderDisplayId = "AA001AA",
    offenderBookingId = 455987L,
    subAccountType = subaccountType,
    postingType = postingType,
    type = type,
    description = "Mock Transaction Test",
    amount = amount,
    reference = null,
    generalLedgerEntries = generalLedgerEntries,
  )

  private fun createSyncOffenderTransactionRequest(
    offenderTransactions: List<OffenderTransaction> = listOf(
      createMockOffenderTransaction(),
    ),
    caseloadId: String = "GMI",
  ): SyncOffenderTransactionRequest = SyncOffenderTransactionRequest(
    transactionId = (1..Long.MAX_VALUE).random(),
    requestId = UUID.randomUUID(),
    caseloadId = caseloadId,
    transactionTimestamp = LocalDateTime.now(),
    createdAt = LocalDateTime.now().minusHours(1),
    createdBy = "JD12345",
    createdByDisplayName = "J Doe",
    lastModifiedAt = null,
    lastModifiedBy = null,
    lastModifiedByDisplayName = null,
    offenderTransactions = offenderTransactions,
  )
}
