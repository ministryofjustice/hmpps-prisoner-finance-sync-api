package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.time.LocalDateTime
import java.util.UUID

class TestBuilders {

  companion object {
    val usedCaseloadIds = mutableListOf<String>()

    fun uniqueCaseloadId(): String {
      var caseload = UUID.randomUUID().toString().substring(0, 3).uppercase()
      while (usedCaseloadIds.contains(caseload)) {
        caseload = UUID.randomUUID().toString().substring(0, 3).uppercase()
      }
      usedCaseloadIds.add(caseload)
      return caseload
    }

    fun createSyncOffenderTransactionRequest(caseloadId: String): SyncOffenderTransactionRequest = SyncOffenderTransactionRequest(
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
          offenderDisplayId = "AA001AA",
          offenderBookingId = 455987L,
          subAccountType = "REG",
          postingType = "DR",
          type = "OT",
          description = "Sub-Account Transfer",
          amount = 162.00,
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = 162.00),
            GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = 162.00),
          ),
        ),
      ),
    )
  }
}
