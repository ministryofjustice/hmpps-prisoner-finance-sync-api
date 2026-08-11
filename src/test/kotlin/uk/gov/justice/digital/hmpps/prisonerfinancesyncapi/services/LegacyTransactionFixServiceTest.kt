package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.CustomException
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
    entrySequence: Int = 2,
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
        entrySequence = entrySequence,
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
  fun `should remove offender transactions from SyncOffenderTransactionRequest when type is OT and no GL entries exist`() {
    val result = legacyTransactionFixService.fixLegacyTransactions(createSyncOffenderTransactionRequest("OT"))
    assertThat(result.offenderTransactions).isNotNull()
    assertThat(result.offenderTransactions.isEmpty()).isTrue
  }

  // in cases like ATOF, OT and some AJ, transfers are represented with 2 offender transactions - one with both postings and one with no postings at all
  // to handle this, we put all necessary information on the postings of the first transaction and remove the second
  @Test
  fun `should consolidate both sides of a transfer transaction into a single offender transaction`() {
    val offenderDisplayId = "AA001AA"
    val legacyTransaction = SyncOffenderTransactionRequest(
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
          entrySequence = 1,
          offenderId = 5306470,
          offenderDisplayId = offenderDisplayId,
          offenderBookingId = 2970777,
          subAccountType = "REG",
          postingType = "CR",
          type = "OT",
          description = "",
          amount = BigDecimal("5.99"),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = BigDecimal("5.99")),
            GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = BigDecimal("5.99")),
          ),
        ),
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 5306470,
          offenderDisplayId = offenderDisplayId,
          offenderBookingId = 2970777,
          subAccountType = "REG",
          postingType = "DR",
          type = "OT",
          description = "",
          amount = BigDecimal("5.99"),
          reference = null,
          generalLedgerEntries = emptyList(),
        ),
      ),
    )

    val fixedTransactions = legacyTransactionFixService.fixLegacyTransactions(legacyTransaction)
    // the second transaction has been removed
    assertThat(fixedTransactions.offenderTransactions).hasSize(1)
    assertThat(fixedTransactions.offenderTransactions[0].generalLedgerEntries).hasSize(2)
    // the first transaction has the respective offenderDisplayIds on the gl entries - in this case the same
    assertThat(fixedTransactions.offenderTransactions[0].generalLedgerEntries[0].offenderDisplayId).isEqualTo(offenderDisplayId)
    assertThat(fixedTransactions.offenderTransactions[0].generalLedgerEntries[1].offenderDisplayId).isEqualTo(offenderDisplayId)
  }

  // In some AJ transfers, we receive transfers between offenders. In this case, the GL entries should include respective offenderDisplayIds
  @Test
  fun `should consolidate offender transactions so gl entries contain respective offenderDisplayIds when 2 offenders are involved`() {
    val offenderA = "AA001AA"
    val offenderB = "AA002BB"
    val legacyTransaction = SyncOffenderTransactionRequest(
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
          entrySequence = 1,
          offenderId = 5306470,
          offenderDisplayId = offenderA,
          offenderBookingId = 2970777,
          subAccountType = "REG",
          postingType = "CR",
          type = "OT",
          description = "",
          amount = BigDecimal("5.99"),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "CR", amount = BigDecimal("5.99")),
            GeneralLedgerEntry(entrySequence = 2, code = 2101, postingType = "DR", amount = BigDecimal("5.99")),
          ),
        ),
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 5306471,
          offenderDisplayId = offenderB,
          offenderBookingId = 2970778,
          subAccountType = "REG",
          postingType = "DR",
          type = "OT",
          description = "",
          amount = BigDecimal("5.99"),
          reference = null,
          generalLedgerEntries = emptyList(),
        ),
      ),
    )
    val fixedTransactions = legacyTransactionFixService.fixLegacyTransactions(legacyTransaction)
    // the second transaction has been removed
    assertThat(fixedTransactions.offenderTransactions).hasSize(1)
    assertThat(fixedTransactions.offenderTransactions[0].generalLedgerEntries).hasSize(2)

    // the first entry is a credit, which relates to offender A
    assertThat(fixedTransactions.offenderTransactions[0].generalLedgerEntries[0].postingType).isEqualTo("CR")
    assertThat(fixedTransactions.offenderTransactions[0].generalLedgerEntries[0].offenderDisplayId).isEqualTo(offenderA)

    // the second entry is a debit, which relates to offender B
    assertThat(fixedTransactions.offenderTransactions[0].generalLedgerEntries[1].postingType).isEqualTo("DR")
    assertThat(fixedTransactions.offenderTransactions[0].generalLedgerEntries[1].offenderDisplayId).isEqualTo(offenderB)
  }

  @Test
  fun `should throw bad request exception when next transaction in transfer pair is of a different type`() {
    val offenderA = "AA001AA"
    val offenderB = "AA002BB"
    val legacyTransaction = SyncOffenderTransactionRequest(
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
          entrySequence = 1,
          offenderId = 5306470,
          offenderDisplayId = offenderA,
          offenderBookingId = 2970777,
          subAccountType = "REG",
          postingType = "CR",
          type = "OT",
          description = "",
          amount = BigDecimal("5.99"),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "CR", amount = BigDecimal("5.99")),
            GeneralLedgerEntry(entrySequence = 2, code = 2101, postingType = "DR", amount = BigDecimal("5.99")),
          ),
        ),
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 5306471,
          offenderDisplayId = offenderB,
          offenderBookingId = 2970778,
          subAccountType = "REG",
          postingType = "DR",
          type = "AJ",
          description = "",
          amount = BigDecimal("5.99"),
          reference = null,
          generalLedgerEntries = emptyList(),
        ),
      ),
    )

    val status = assertThrows(CustomException::class.java) {
      legacyTransactionFixService.fixLegacyTransactions(legacyTransaction)
    }.status
    assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST)
  }

  @Test
  fun `should remove offender transactions from SyncOffenderTransactionRequest when type is ATOF and no GL entries exist`() {
    val result = legacyTransactionFixService.fixLegacyTransactions(createSyncOffenderTransactionRequest("ATOF"))
    assertThat(result.offenderTransactions).isNotNull()
    assertThat(result.offenderTransactions.isEmpty()).isTrue
  }

  @Test
  @Disabled
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
    assertThat(result.offenderTransactions[0].generalLedgerEntries[0].code).isEqualTo(1101)
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
