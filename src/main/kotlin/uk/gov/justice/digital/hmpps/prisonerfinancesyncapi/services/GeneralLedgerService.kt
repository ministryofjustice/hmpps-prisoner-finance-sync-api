package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlPostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.util.UUID

@Service("generalLedgerService")
open class GeneralLedgerService(
  private val glClient: GeneralLedgerApiClient,
  private val timeConversionService: TimeConversionService,
) : LedgerTransactionProcessor {

  private companion object {
    private val PRISONER_SUB_ACCOUNTS = mapOf(
      2101 to "CASH",
      2102 to "SPND",
      2103 to "SAV",
    )
  }

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): UUID {
    if (request.offenderTransactions.isEmpty()) {
      throw IllegalArgumentException("No offender transactions provided in the request.")
    }

    var lastTransactionId: UUID = UUID.randomUUID()

    request.offenderTransactions.forEach { offenderTx ->

      val debitEntry = offenderTx.generalLedgerEntries.firstOrNull { it.postingType == "DR" }
        ?: throw IllegalStateException("Transaction must have a Debit (DR) entry")

      val creditEntry = offenderTx.generalLedgerEntries.firstOrNull { it.postingType == "CR" }
        ?: throw IllegalStateException("Transaction must have a Credit (CR) entry")

      // Use the Transaction Type (e.g. "CANT", "ADV") for mapping prison keys
      val txType = offenderTx.type

      // Resolve UUIDs using the composite key logic
      val debtorUuid = resolveFullAccountPath(debitEntry.code, request.caseloadId, offenderTx.offenderDisplayId, txType)
      val creditorUuid =
        resolveFullAccountPath(creditEntry.code, request.caseloadId, offenderTx.offenderDisplayId, txType)

      // Convert Amount to Pence (Long) safely using BigDecimal
      val amountInPence = BigDecimal.valueOf(debitEntry.amount)
        .multiply(BigDecimal("100"))
        .toLong()

      // Create Postings List
      val postings = listOf(
        GlPostingRequest(
          subAccountId = debtorUuid,
          type = PostingType.DR,
          amount = amountInPence,
        ),
        GlPostingRequest(
          subAccountId = creditorUuid,
          type = PostingType.CR,
          amount = amountInPence,
        ),
      )

      val payload = GlTransactionRequest(
        timestamp = timeConversionService.toUtcInstant(request.transactionTimestamp),
        amount = amountInPence,
        postings = postings,
        reference = request.transactionId.toString(),
        description = offenderTx.description ?: "Sync Transaction",
      )

      lastTransactionId = glClient.postTransaction(payload)
    }

    return lastTransactionId
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = throw NotImplementedError()

  /**
   * Resolves the full path: Parent (Prisoner/Prison) -> Sub-Account
   * Returns the Sub-Account UUID.
   */
  private fun resolveFullAccountPath(legacyCode: Int, prisonId: String, offenderNo: String, txType: String?): UUID {
    val isPrisonerAccount = PRISONER_SUB_ACCOUNTS.containsKey(legacyCode)

    val (parentRef, subAccountRef) = if (isPrisonerAccount) {
      Pair(offenderNo, PRISONER_SUB_ACCOUNTS[legacyCode]!!)
    } else {
      val compositeRef = if (txType != null) "$legacyCode:$txType" else legacyCode.toString()
      Pair(prisonId, compositeRef)
    }

    val parentUuid = resolveParentAccount(parentRef)

    return resolveSubAccount(parentUuid, parentRef, subAccountRef)
  }

  private fun resolveParentAccount(reference: String): UUID {
    val existing = glClient.findAccountByReference(reference)
    if (existing != null) return existing.id
    return glClient.createAccount(reference).id
  }

  private fun resolveSubAccount(parentId: UUID, parentRef: String, subAccountRef: String): UUID {
    val existing = glClient.findSubAccount(parentRef, subAccountRef)
    if (existing != null) return existing.id
    return glClient.createSubAccount(parentId, subAccountRef).id
  }
}
