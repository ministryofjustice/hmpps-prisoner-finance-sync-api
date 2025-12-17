package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): UUID {
    if (request.offenderTransactions.isEmpty()) {
      throw IllegalArgumentException("No offender transactions provided in the request.")
    }

    // TODO: Should we return the ID of the LAST transaction posted as the receipt (or generate a batch ID if needed)
    var lastTransactionId: UUID = UUID.randomUUID()

    request.offenderTransactions.forEach { offenderTx ->

      // 1. Identify the Prisoner's Side
      val prisonerRef = offenderTx.offenderDisplayId
      val isPrisonerCredit = offenderTx.postingType == "CR"

      // 2. Identify the Contra Side (The GL Entry that balances the prisoner)
      val contraEntry = offenderTx.generalLedgerEntries.firstOrNull { glEntry ->
        glEntry.postingType != offenderTx.postingType
      } ?: throw IllegalStateException("No balancing GL entry found for offender transaction ${request.transactionId}")

      // Construct a unique reference for the GL Account (e.g., "MDI-1502")
      val contraRef = "${request.caseloadId}-${contraEntry.code}"

      val prisonerUuid = resolveAccountUuid(
        reference = prisonerRef,
        defaultName = "Prisoner $prisonerRef",
      )

      val contraUuid = resolveAccountUuid(
        reference = contraRef,
        defaultName = "GL Code ${contraEntry.code} (${request.caseloadId})",
      )

      val (debtor, creditor) = if (isPrisonerCredit) {
        Pair(contraUuid, prisonerUuid)
      } else {
        Pair(prisonerUuid, contraUuid)
      }

      val payload = GlTransactionRequest(
        timestamp = timeConversionService.toUtcInstant(request.transactionTimestamp),
        amount = BigDecimal.valueOf(offenderTx.amount),
        creditorAccount = creditor,
        debtorAccount = debtor,
        reference = request.transactionId.toString(),
        description = offenderTx.description,
      )

      lastTransactionId = glClient.postTransaction(payload)
    }

    return lastTransactionId
  }

  private fun resolveAccountUuid(reference: String, defaultName: String): UUID {
    val existing = glClient.findAccountByReference(reference)

    if (existing != null) {
      return existing.id
    }

    val newAccount = glClient.createAccount(
      name = defaultName,
      reference = reference,
    )

    return newAccount.id
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = throw NotImplementedError()
}
