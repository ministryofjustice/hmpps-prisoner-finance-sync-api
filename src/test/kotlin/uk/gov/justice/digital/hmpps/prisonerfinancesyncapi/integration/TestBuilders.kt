package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

class TestBuilders {

  companion object {
    val usedCaseloadIds = mutableListOf<String>()
    val usedPrisonNumbers = mutableSetOf<String>()

    fun uniqueCaseloadId(): String {
      var caseload = UUID.randomUUID().toString().substring(0, 3).uppercase()
      while (usedCaseloadIds.contains(caseload)) {
        caseload = UUID.randomUUID().toString().substring(0, 3).uppercase()
      }
      usedCaseloadIds.add(caseload)
      return caseload
    }

    fun uniquePrisonNumber(): String {
      var displayId: String

      do {
        val firstLetter = ('A'..'Z').random()
        val digits = Random.nextInt(0, 10000).toString().padStart(4, '0')
        val lastLetters = "${('A'..'Z').random()}${('A'..'Z').random()}"

        displayId = "$firstLetter$digits$lastLetters"
      } while (usedPrisonNumbers.contains(displayId))

      usedPrisonNumbers.add(displayId)
      return displayId
    }

    fun createSyncOffenderTransactionRequest(
      caseloadId: String,
      prisonNumber: String,
      transactionType: String = "OT",
      generalLedgerEntries: List<GeneralLedgerEntry> = listOf(
        GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = BigDecimal("162.00")),
        GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = BigDecimal("162.00")),
      ),
      amount: BigDecimal = BigDecimal("162.00"),
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
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1015388L,
          offenderDisplayId = prisonNumber,
          offenderBookingId = 455987L,
          subAccountType = "REG",
          postingType = "DR",
          type = transactionType,
          description = "Sub-Account Transfer",
          amount = amount,
          reference = null,
          generalLedgerEntries = generalLedgerEntries,
        ),
      ),
    )
  }
}
