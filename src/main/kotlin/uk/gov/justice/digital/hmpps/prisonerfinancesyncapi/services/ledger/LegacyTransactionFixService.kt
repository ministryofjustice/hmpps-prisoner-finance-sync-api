package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.CustomException
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

const val MIGRATION_CLEARING_ACCOUNT = 1101

@Service
class LegacyTransactionFixService {

  private companion object {
    private const val TRANSACTION_TYPE_TIR = "TIR"
  }

  fun fixLegacyTransactions(request: SyncOffenderTransactionRequest): SyncOffenderTransactionRequest {
    val fixedOffenderTransactions = request.offenderTransactions.withIndex().mapNotNull { (index, offenderTransaction) ->

      when {
        offenderTransaction.type == TRANSACTION_TYPE_TIR && offenderTransaction.generalLedgerEntries.isEmpty() -> return@mapNotNull offenderTransaction.copy(
          generalLedgerEntries = generateGeneralLedgerEntries(offenderTransaction),
        )
        offenderTransaction.generalLedgerEntries.isEmpty() -> return@mapNotNull null
        isPrisonerSubaccountTransaction(offenderTransaction.generalLedgerEntries) -> return@mapNotNull fixPrisonerToPrisonerTransfer(request, index)
        else -> return@mapNotNull offenderTransaction
      }
    }

    return request.copy(offenderTransactions = fixedOffenderTransactions)
  }

  private fun fixPrisonerToPrisonerTransfer(request: SyncOffenderTransactionRequest, index: Int): OffenderTransaction {
    val offenderTransaction = request.offenderTransactions[index]

    val nextTransaction = request.offenderTransactions.getOrNull(index + 1)
      ?: throw CustomException("No next transaction found for prisoner to prisoner transfer", status = HttpStatus.BAD_REQUEST)

    if (offenderTransaction.type != nextTransaction.type) throw CustomException("Mismatched transaction types for prisoner to prisoner transfer: ${offenderTransaction.type} != ${nextTransaction.type}. Entry sequence ${offenderTransaction.entrySequence}", status = HttpStatus.BAD_REQUEST)

    val glEntryForCurrent = offenderTransaction.generalLedgerEntries.find { it.postingType == offenderTransaction.postingType }
      ?.copy(offenderDisplayId = offenderTransaction.offenderDisplayId)
      ?: throw CustomException("No matching posting type for current transaction for entry sequence ${offenderTransaction.entrySequence}", status = HttpStatus.BAD_REQUEST)

    val glEntryForNext = offenderTransaction.generalLedgerEntries.find { it.postingType == nextTransaction.postingType }
      ?.copy(offenderDisplayId = nextTransaction.offenderDisplayId)
      ?: throw CustomException("No matching posting type for next transaction for entry sequence ${nextTransaction.entrySequence}", status = HttpStatus.BAD_REQUEST)

    return offenderTransaction.copy(
      generalLedgerEntries = listOf(glEntryForCurrent, glEntryForNext),
    )
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

  private fun isPrisonerSubaccountTransaction(glEntries: List<GeneralLedgerEntry>) = glEntries.all { listOf(2101, 2102, 2103).contains(it.code) } && glEntries.isNotEmpty()
}
