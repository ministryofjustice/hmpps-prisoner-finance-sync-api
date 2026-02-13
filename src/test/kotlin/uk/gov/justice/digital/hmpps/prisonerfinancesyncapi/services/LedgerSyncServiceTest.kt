package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Prison
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.AccountService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerSyncService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LegacyTransactionFixService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.PrisonService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.TransactionService
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class LedgerSyncServiceTest {

  @Mock private lateinit var prisonService: PrisonService

  @Mock private lateinit var accountService: AccountService

  @Mock private lateinit var transactionService: TransactionService

  @Mock private lateinit var timeConversionService: TimeConversionService

  @Mock private lateinit var legacyTransactionFixService: LegacyTransactionFixService

  @Mock private lateinit var telemetryClient: TelemetryClient

  @InjectMocks private lateinit var ledgerSyncService: LedgerSyncService

  @Captor
  private lateinit var entriesCaptor: ArgumentCaptor<List<Triple<Long, BigDecimal, PostingType>>>

  private val fixedNow = Instant.parse("2023-10-01T10:00:00Z")

  @Nested
  @DisplayName("syncOffenderTransaction")
  inner class SyncOffenderTransaction {

    @Test
    fun `should throw exception when no offender transactions provided`() {
      val request = createOffenderRequest(emptyList())

      assertThatThrownBy {
        ledgerSyncService.syncOffenderTransaction(request)
      }.isInstanceOf(IllegalArgumentException::class.java)
        .hasMessage("No offender transactions provided in the request.")
    }

    @Test
    fun `should return random UUID when fixed request is empty (Filtered)`() {
      val tx = createOffenderTransaction()
      val request = createOffenderRequest(listOf(tx))

      whenever(legacyTransactionFixService.fixLegacyTransactions(request))
        .thenReturn(request.copy(offenderTransactions = emptyList()))

      val result = ledgerSyncService.syncOffenderTransaction(request)

      assertThat(result).isNotNull()
      verify(prisonService, never()).getPrison(any())
    }

    @Test
    fun `should process transaction successfully when prison and account exist`() {
      val tx = createOffenderTransaction()
      val request = createOffenderRequest(listOf(tx))
      val prisonDbId = 100L
      val accountDbId = 200L

      whenever(legacyTransactionFixService.fixLegacyTransactions(request)).thenReturn(request)
      whenever(prisonService.getPrison(request.caseloadId)).thenReturn(Prison(id = prisonDbId, code = request.caseloadId))
      whenever(timeConversionService.toUtcInstant(request.transactionTimestamp)).thenReturn(fixedNow)

      val account = createMockAccount(id = accountDbId, prisonId = prisonDbId)
      whenever(accountService.resolveAccount(eq(tx.generalLedgerEntries[0].code), eq(tx.offenderDisplayId), eq(prisonDbId)))
        .thenReturn(account)

      val result = ledgerSyncService.syncOffenderTransaction(request)

      assertThat(result).isNotNull()

      verify(transactionService).recordTransaction(
        eq(tx.type),
        eq(tx.description),
        capture(entriesCaptor),
        eq(fixedNow),
        eq(request.transactionId),
        eq(result),
        eq(request.caseloadId),
        eq(null),
      )

      val entries = entriesCaptor.value
      assertThat(entries).hasSize(1)
      assertThat(entries[0].first).isEqualTo(accountDbId)
      assertThat(entries[0].second).isEqualByComparingTo(BigDecimal.valueOf(10.0))

      verify(telemetryClient).trackEvent(eq("nomis-to-prisoner-finance-sync-offender-transaction"), any(), eq(null))
    }

    @Test
    fun `should create prison if it does not exist`() {
      val tx = createOffenderTransaction()
      val request = createOffenderRequest(listOf(tx))
      val prisonDbId = 100L
      val accountDbId = 200L

      whenever(legacyTransactionFixService.fixLegacyTransactions(request)).thenReturn(request)
      whenever(prisonService.getPrison(request.caseloadId)).thenReturn(null)
      whenever(prisonService.createPrison(request.caseloadId)).thenReturn(Prison(id = prisonDbId, code = request.caseloadId))
      whenever(timeConversionService.toUtcInstant(any())).thenReturn(fixedNow)

      val account = createMockAccount(id = accountDbId, prisonId = prisonDbId)
      whenever(accountService.resolveAccount(any(), any(), eq(prisonDbId))).thenReturn(account)

      ledgerSyncService.syncOffenderTransaction(request)

      verify(prisonService).createPrison(request.caseloadId)
    }
  }

  @Nested
  @DisplayName("syncGeneralLedgerTransaction")
  inner class SyncGeneralLedgerTransaction {

    @Test
    fun `should throw exception when entries list is empty`() {
      val request = createGlRequest(emptyList())
      assertThatThrownBy { ledgerSyncService.syncGeneralLedgerTransaction(request) }
        .isInstanceOf(IllegalArgumentException::class.java)
    }
  }

  private fun createMockAccount(id: Long, prisonId: Long) = Account(
    id = id,
    uuid = UUID.randomUUID(),
    prisonId = prisonId,
    name = "Test Account",
    accountType = AccountType.GENERAL_LEDGER,
    accountCode = 1234,
    postingType = PostingType.DR,
  )

  private fun createOffenderTransaction() = OffenderTransaction(
    offenderDisplayId = "A1234AA",
    offenderId = 1L,
    offenderBookingId = 100L,
    entrySequence = 1,
    subAccountType = "SPND",
    postingType = "DR",
    type = "CANT",
    description = "Canteen Spend",
    amount = BigDecimal("10.0"),
    reference = "REF",
    generalLedgerEntries = listOf(GeneralLedgerEntry(1, 1234, "DR", BigDecimal("10.0"))),
  )

  private fun createOffenderRequest(transactions: List<OffenderTransaction>) = SyncOffenderTransactionRequest(
    transactionId = 123L,
    requestId = UUID.randomUUID(),
    caseloadId = "MDI",
    transactionTimestamp = LocalDateTime.now(),
    createdAt = LocalDateTime.now(),
    createdBy = "TEST_USER",
    createdByDisplayName = "Test User",
    lastModifiedAt = null,
    lastModifiedBy = null,
    lastModifiedByDisplayName = null,
    offenderTransactions = transactions,
  )

  private fun createGlRequest(entries: List<GeneralLedgerEntry>) = SyncGeneralLedgerTransactionRequest(
    transactionId = 456L,
    requestId = UUID.randomUUID(),
    description = "Adjustment",
    reference = "REF-999",
    caseloadId = "MDI",
    transactionType = "ADJ",
    transactionTimestamp = LocalDateTime.now(),
    createdAt = LocalDateTime.now(),
    createdBy = "TEST_USER",
    createdByDisplayName = "Test User",
    lastModifiedAt = null,
    lastModifiedBy = null,
    lastModifiedByDisplayName = null,
    generalLedgerEntries = entries,
  )
}
