package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest

/**
 * Service responsible for handling known data inconsistencies originating
 * from the legacy finance system before transactions are synchronized into the new ledger.
 *
 * This service performs two primary functions
 *
 * 1. Ignores offender transactions for types (OT and ATOF with entrySequence=2) with no GL entries.
 * 2. For TIR (Transfer In Regular) transaction type, if the GL entries are missing, this service generates
 * the required General Ledger entries. The generated entries use the 9999 (Migration Clearing Account)
 * to ensure the prisoner's sub-account balance matches the legacy system's balance,
 * without affecting the primary TIR General Ledger accounts to help maintain sync with NOMIS.
 */
@Service
class LegacyTransactionFixService {

  private companion object {
    private const val TRANSACTION_TYPE_TIR = "TIR"
    private val TRANSACTION_TYPES_SKIPPED_IF_NO_GL_ENTRIES = setOf("OT", "ATOF")
  }

  fun fixLegacyTransactions(request: SyncOffenderTransactionRequest): SyncOffenderTransactionRequest {
    val fixedOffenderTransactions = request.offenderTransactions.mapNotNull { offenderTransaction ->

      if (offenderTransaction.generalLedgerEntries.isEmpty() &&
        TRANSACTION_TYPES_SKIPPED_IF_NO_GL_ENTRIES.contains(offenderTransaction.type) &&
        offenderTransaction.entrySequence == 2
      ) {
        null // Skip this transaction as no GL entries
      } else if (offenderTransaction.type == TRANSACTION_TYPE_TIR && offenderTransaction.generalLedgerEntries.isEmpty()) {
        offenderTransaction.copy(
          generalLedgerEntries = generateGeneralLedgerEntries(offenderTransaction),
        )
      } else {
        offenderTransaction
      }
    }

    return request.copy(offenderTransactions = fixedOffenderTransactions)
  }

  private fun generateGeneralLedgerEntries(offenderTransaction: OffenderTransaction): List<GeneralLedgerEntry> {
    val drEntry = GeneralLedgerEntry(
      entrySequence = 1,
      code = MIGRATION_CLEARING_ACCOUNT,
      postingType = "DR",
      amount = offenderTransaction.amount,
    )

    val crEntry = GeneralLedgerEntry(
      entrySequence = 2,
      code = getAccountCodeFromType(offenderTransaction.subAccountType),
      postingType = "CR",
      amount = offenderTransaction.amount,
    )

    return listOf(drEntry, crEntry)
  }

  private fun getAccountCodeFromType(subAccountType: String): Int = when (subAccountType) {
    "REG" -> 2101
    "SAV" -> 2103
    "SPND" -> 2102
    else -> throw IllegalArgumentException("Unsupported subAccountType : $subAccountType")
  }
}
