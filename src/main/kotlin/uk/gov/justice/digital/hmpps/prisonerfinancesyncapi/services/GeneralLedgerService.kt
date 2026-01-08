package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionRequest
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

      // Resolve Parent -> Sub-Account hierarchy for both sides
      val debtorUuid = resolveFullAccountPath(debitEntry.code, request.caseloadId, offenderTx.offenderDisplayId)
      val creditorUuid = resolveFullAccountPath(creditEntry.code, request.caseloadId, offenderTx.offenderDisplayId)

      val payload = GlTransactionRequest(
        timestamp = timeConversionService.toUtcInstant(request.transactionTimestamp),
        amount = BigDecimal.valueOf(debitEntry.amount),
        creditorAccount = creditorUuid,
        debtorAccount = debtorUuid,
        reference = request.transactionId.toString(),
        description = offenderTx.description,
      )

      lastTransactionId = glClient.postTransaction(payload)
    }

    return lastTransactionId
  }

  /**
   * Resolves the full path: Parent (Prisoner/Prison) -> Sub-Account (Spends/1502/etc)
   */
  private fun resolveFullAccountPath(legacyCode: Int, prisonId: String, offenderNo: String): UUID {
    val isPrisonerAccount = PRISONER_SUB_ACCOUNTS.containsKey(legacyCode)

    val (parentRef, parentName, subAccountRef) = if (isPrisonerAccount) {
      Triple(offenderNo, "Prisoner $offenderNo", PRISONER_SUB_ACCOUNTS[legacyCode]!!)
    } else {
      Triple(prisonId, "Prison $prisonId", legacyCode.toString())
    }

    val parentUuid = resolveParentAccount(parentRef, parentName)
    return resolveSubAccount(parentUuid, subAccountRef, "Sub-Account $subAccountRef")
  }

  private fun resolveParentAccount(reference: String, name: String): UUID {
    val existing = glClient.findAccountByReference(reference)
    if (existing != null) return existing.id
    return glClient.createAccount(name, reference).id
  }

  private fun resolveSubAccount(parentId: UUID, reference: String, name: String): UUID {
    val existing = glClient.findSubAccount(parentId, reference)
    if (existing != null) return existing.id
    return glClient.createSubAccount(parentId, reference, name).id
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = throw NotImplementedError()
}
