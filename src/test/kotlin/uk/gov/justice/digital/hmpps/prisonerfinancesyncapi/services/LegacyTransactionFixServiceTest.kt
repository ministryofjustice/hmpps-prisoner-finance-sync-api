package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LegacyTransactionFixService
import java.time.LocalDateTime
import java.util.UUID
import kotlin.collections.List

@ExtendWith(MockitoExtension::class)
@DisplayName("legacy transaction fix tests")
class LegacyTransactionFixServiceTest {

  fun createGeneralLedgerEntries(includeGeneralLedgerEntries: Boolean): List<GeneralLedgerEntry> {
    val generalLedgerEntries = listOf(
      GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = 5.99),
      GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = 5.99),
    )

    return if (includeGeneralLedgerEntries) generalLedgerEntries else emptyList()
  }

  fun createSyncOffenderTransactionRequest(offenderTransactionType: String, includeGeneralLedgerEntries: Boolean = false): SyncOffenderTransactionRequest = SyncOffenderTransactionRequest(
    transactionId = 485368707,
    requestId = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef"),
    caseloadId = "LEI",
    transactionTimestamp = LocalDateTime.now(),
    createdAt = LocalDateTime.now(),
    createdBy = "JD12345",
    createdByDisplayName = "J Doe",
    lastModifiedAt = null,
    lastModifiedBy = null,
    lastModifiedByDisplayName = null,
    listOf(
      OffenderTransaction(
        entrySequence = 2,
        offenderId = 5306470,
        offenderDisplayId = "AA001AA",
        offenderBookingId = 2970777,
        subAccountType = "SPND",
        postingType = "CR",
        type = offenderTransactionType,
        description = "",
        amount = 5.99,
        reference = null,
        generalLedgerEntries = createGeneralLedgerEntries(includeGeneralLedgerEntries),
      ),
    ),
  )

  @InjectMocks
  private lateinit var legacyTransactionFixService: LegacyTransactionFixService

  @Test
  fun `should return true when offender has a transaction with an entry sequence of two and transaction type is sub-account transfer`() {
    val result = legacyTransactionFixService.fixLegacyTransactions(createSyncOffenderTransactionRequest("OT"))
    assertThat(result.offenderTransactions).isNotNull()
    assertThat(result.offenderTransactions.isEmpty()).isTrue
  }

  @Test
  fun `should return true when offender has a transaction with an entry sequence of two and transaction type is cash to spends transfer`() {
    val result = legacyTransactionFixService.fixLegacyTransactions(createSyncOffenderTransactionRequest("ATOF"))
    assertThat(result.offenderTransactions).isNotNull()
    assertThat(result.offenderTransactions.isEmpty()).isTrue
  }

  @Test
  fun `should return true when transaction type is transfer and general ledgers are included`() {
    val result = legacyTransactionFixService.fixLegacyTransactions(createSyncOffenderTransactionRequest("TIR", true))
    assertThat(result.offenderTransactions.size).isEqualTo(1)
    assertThat(result.offenderTransactions[0].generalLedgerEntries.size).isEqualTo(2)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].entrySequence).isEqualTo(1)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].code).isEqualTo(2101)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].postingType).isEqualTo("DR")
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].amount).isEqualTo(5.99)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].entrySequence).isEqualTo(2)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].code).isEqualTo(2102)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].postingType).isEqualTo("CR")
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].amount).isEqualTo(5.99)
  }

  @Test
  fun `should return true when transaction type is transfer in regular and general ledgers entries are missing`() {
    val result = legacyTransactionFixService.fixLegacyTransactions(createSyncOffenderTransactionRequest("TIR", false))
    assertThat(result.offenderTransactions).isNotNull()
    assertThat(result.offenderTransactions.size).isEqualTo(1)
    assertThat(result.offenderTransactions[0].generalLedgerEntries.size).isEqualTo(2)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].entrySequence).isEqualTo(1)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].code).isEqualTo(9999)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].postingType).isEqualTo("DR")
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].amount).isEqualTo(result.offenderTransactions[0].amount)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].entrySequence).isEqualTo(2)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].code).isEqualTo(2102)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].postingType).isEqualTo("CR")
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].amount).isEqualTo(result.offenderTransactions[0].amount)
  }
}
