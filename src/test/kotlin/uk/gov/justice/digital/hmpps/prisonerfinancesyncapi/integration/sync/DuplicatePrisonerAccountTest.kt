package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.TestBuilders.Companion.uniquePrisonNumber
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class DuplicatePrisonerAccountTest : IntegrationTestBase() {

  @Test
  fun `should not create duplicate prisoner accounts in race-condition`() {
    val testOffenderId = 123L
    val prisonNumber = uniquePrisonNumber()
    val transferAmount = BigDecimal("324.00")

    val transferReq1 = createSyncOffenderTransactionRequest(
      caseloadId = "MDI",
      offenderTransactions = listOf(
        createTestOffenderTransaction(
          type = "TOR",
          amount = BigDecimal("0.00"),
          offenderDisplayId = prisonNumber,
          offenderId = testOffenderId,
          postingType = "DR",
          subaccountType = "REG",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = BigDecimal("0.00")),
            GeneralLedgerEntry(entrySequence = 2, code = 1101, postingType = "CR", amount = BigDecimal("0.00")),
          ),
        ),
        createTestOffenderTransaction(
          type = "TOR",
          amount = transferAmount,
          offenderDisplayId = prisonNumber,
          offenderId = testOffenderId,
          postingType = "DR",
          subaccountType = "SPND",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 3, code = 2102, postingType = "DR", amount = transferAmount),
            GeneralLedgerEntry(entrySequence = 4, code = 1101, postingType = "CR", amount = transferAmount),
          ),
        ),
        createTestOffenderTransaction(
          type = "TOR",
          amount = BigDecimal("0.00"),
          offenderDisplayId = prisonNumber,
          offenderId = testOffenderId,
          postingType = "DR",
          subaccountType = "SAV",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 5, code = 2103, postingType = "DR", amount = BigDecimal("0.00")),
            GeneralLedgerEntry(entrySequence = 6, code = 1101, postingType = "CR", amount = BigDecimal("0.00")),
          ),
        ),
      ),
    )

    val transferReq2 = createSyncOffenderTransactionRequest(
      caseloadId = "KMI",
      offenderTransactions = listOf(
        createTestOffenderTransaction(
          type = "TIR",
          amount = BigDecimal("0.00"),
          offenderDisplayId = prisonNumber,
          offenderId = testOffenderId,
          postingType = "CR",
          subaccountType = "REG",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = 1101, postingType = "DR", amount = BigDecimal("0.00")),
            GeneralLedgerEntry(entrySequence = 2, code = 2101, postingType = "CR", amount = BigDecimal("0.00")),
          ),
        ),
        createTestOffenderTransaction(
          type = "TOR",
          amount = transferAmount,
          offenderDisplayId = prisonNumber,
          offenderId = testOffenderId,
          postingType = "DR",
          subaccountType = "SPND",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 3, code = 1101, postingType = "DR", amount = transferAmount),
            GeneralLedgerEntry(entrySequence = 4, code = 2102, postingType = "CR", amount = transferAmount),
          ),
        ),
        createTestOffenderTransaction(
          type = "TOR",
          amount = BigDecimal("0.00"),
          offenderDisplayId = prisonNumber,
          offenderId = testOffenderId,
          postingType = "DR",
          subaccountType = "SAV",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 5, code = 1101, postingType = "DR", amount = BigDecimal("0.00")),
            GeneralLedgerEntry(entrySequence = 6, code = 2103, postingType = "CR", amount = BigDecimal("0.00")),
          ),
        ),
      ),
    )

    executeInParallel(
      { postTransaction(transferReq1) },
      { postTransaction(transferReq2) },
    )

    webTestClient
      .get()
      .uri("/prisoners/{prisonNumber}/accounts", prisonNumber)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.items[*].code")
      .value<List<Int>> { codes ->
        val duplicates = codes.groupBy { it }.filter { it.value.size > 1 }
        assertThat(duplicates)
          .withFailMessage("Duplicate account codes found: $duplicates")
          .isEmpty()
        assertThat(codes).containsExactlyInAnyOrder(2101, 2102, 2103)
      }
      .jsonPath("$.items.length()")
      .isEqualTo(3)
  }

  /**
   * Helper to run two tasks in parallel starting at the exact same moment.
   * Handles thread cleanup and propagates exceptions to the main test thread.
   */
  private fun executeInParallel(task1: () -> Unit, task2: () -> Unit) {
    val executor = Executors.newFixedThreadPool(2)
    val latch = CountDownLatch(1)

    try {
      val futures = listOf(task1, task2).map { task ->
        CompletableFuture.runAsync({
          try {
            latch.await() // Wait for signal
            task()
          } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
          }
        }, executor)
      }

      latch.countDown() // Start both threads
      // Wait for completion and rethrow any exceptions (like assertion errors)
      CompletableFuture.allOf(*futures.toTypedArray()).join()
    } finally {
      executor.shutdown()
    }
  }

  private fun postTransaction(request: SyncOffenderTransactionRequest) {
    webTestClient.post()
      .uri("/sync/offender-transactions")
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .bodyValue(request)
      .exchange()
      .expectStatus().isCreated
  }

  private fun createTestOffenderTransaction(
    type: String = "OT",
    subaccountType: String = "REG",
    postingType: String = "DR",
    offenderDisplayId: String = "AA001AA",
    amount: BigDecimal = BigDecimal("162.00"),
    offenderId: Long = 1015388L,
    generalLedgerEntries: List<GeneralLedgerEntry> = listOf(
      GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = BigDecimal("162.00")),
      GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = BigDecimal("162.00")),
    ),
  ): OffenderTransaction = OffenderTransaction(
    entrySequence = 1,
    offenderId = offenderId,
    offenderDisplayId = offenderDisplayId,
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
      createTestOffenderTransaction(),
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
