package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LegacyTransactionFixService
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.collections.List

@DisplayName("legacy transaction fix tests")
class LegacyTransactionFixServiceTest {

  private lateinit var legacyTransactionFixService: LegacyTransactionFixService

  @BeforeEach
  fun setUp() {
    legacyTransactionFixService = LegacyTransactionFixService()
  }

  fun createGeneralLedgerEntries(includeGeneralLedgerEntries: Boolean): List<GeneralLedgerEntry> {
    val generalLedgerEntries = listOf(
      GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = BigDecimal("5.99")),
      GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = BigDecimal("5.99")),
    )
    return if (includeGeneralLedgerEntries) generalLedgerEntries else emptyList()
  }

  fun createSyncOffenderTransactionRequest(
    offenderTransactionType: String,
    includeGeneralLedgerEntries: Boolean = false,
    subAccountType: String = "SPND",
  ): SyncOffenderTransactionRequest = SyncOffenderTransactionRequest(
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
        subAccountType = subAccountType,
        postingType = "CR",
        type = offenderTransactionType,
        description = "",
        amount = BigDecimal("5.99"),
        reference = null,
        generalLedgerEntries = createGeneralLedgerEntries(includeGeneralLedgerEntries),
      ),
    ),
  )

  @Test
  fun `should preserve SyncOffenderTransactionRequest when type is OT and no GL entries exist`() {
    val result = legacyTransactionFixService.fixLegacyTransactions(createSyncOffenderTransactionRequest("OT"))
    assertThat(result.offenderTransactions).isNotNull()
    assertThat(result.offenderTransactions.isEmpty()).isTrue
  }

  @Test
  fun `should preserve SyncOffenderTransactionRequest when type is ATOF and no GL entries exist`() {
    val result = legacyTransactionFixService.fixLegacyTransactions(createSyncOffenderTransactionRequest("ATOF"))
    assertThat(result.offenderTransactions).isNotNull()
    assertThat(result.offenderTransactions.isEmpty()).isTrue
  }

  @Test
  fun `should preserve existing GL entries when type is TIR and GL entries exist`() {
    val result = legacyTransactionFixService.fixLegacyTransactions(
      createSyncOffenderTransactionRequest(
        "TIR",
        true,
      ),
    )
    assertThat(result.offenderTransactions.size).isEqualTo(1)
    assertThat(result.offenderTransactions[0].generalLedgerEntries.size).isEqualTo(2)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].entrySequence).isEqualTo(1)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].code).isEqualTo(2101)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].postingType).isEqualTo("DR")
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].amount).isEqualTo(BigDecimal("5.99"))
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].entrySequence).isEqualTo(2)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].code).isEqualTo(2102)
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].postingType).isEqualTo("CR")
    assertThat(result.offenderTransactions[0].generalLedgerEntries[1].amount).isEqualTo(BigDecimal("5.99"))
  }

  @Test
  fun `should generate GL entries when transaction type is TIR and GL entries are missing`() {
    val result = legacyTransactionFixService.fixLegacyTransactions(createSyncOffenderTransactionRequest("TIR"))
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

  @Test
  fun `should generate exception when OT sub account type is invalid`() {
    val ex = assertThrows(IllegalArgumentException::class.java) {
      legacyTransactionFixService.fixLegacyTransactions(
        createSyncOffenderTransactionRequest(
          "TIR",
          includeGeneralLedgerEntries = false,
          "AAA",
        ),
      )
    }
    assert(ex.message!!.contains("Unsupported subAccountType"))
  }
}
