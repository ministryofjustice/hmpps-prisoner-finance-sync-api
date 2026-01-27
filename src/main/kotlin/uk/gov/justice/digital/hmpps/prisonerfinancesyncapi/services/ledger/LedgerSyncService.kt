package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.LedgerService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import java.math.BigDecimal
import java.util.UUID

@Service("internalLedgerService")
open class LedgerSyncService(
  private val prisonService: PrisonService,
  private val accountService: AccountService,
  private val transactionService: TransactionService,
  private val timeConversionService: TimeConversionService,
  private val legacyTransactionFixService: LegacyTransactionFixService,
  private val telemetryClient: TelemetryClient,
) : LedgerService {

  private companion object {
    private const val TELEMETRY_PRISONER_PREFIX = "nomis-to-prisoner-finance-sync"
  }

  @Transactional
  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): UUID {
    if (request.offenderTransactions.isEmpty()) {
      throw IllegalArgumentException("No offender transactions provided in the request.")
    }

    val fixedRequest = legacyTransactionFixService.fixLegacyTransactions(request)

    if (fixedRequest.offenderTransactions.isEmpty()) {
      return UUID.randomUUID()
    }

    val prison = prisonService.getPrison(fixedRequest.caseloadId)
      ?: prisonService.createPrison(fixedRequest.caseloadId)

    val transactionTimestamp = timeConversionService.toUtcInstant(fixedRequest.transactionTimestamp)
    val synchronizedTransactionId = UUID.randomUUID()

    fixedRequest.offenderTransactions.forEach { offenderTransaction ->
      val transactionEntries = offenderTransaction.generalLedgerEntries.map { glEntry ->
        val account = accountService.resolveAccount(
          glEntry.code,
          offenderTransaction.offenderDisplayId,
          prison.id!!,
        )
        Triple(account.id!!, BigDecimal.valueOf(glEntry.amount), PostingType.valueOf(glEntry.postingType))
      }

      transactionService.recordTransaction(
        transactionType = offenderTransaction.type,
        description = offenderTransaction.description,
        entries = transactionEntries,
        transactionTimestamp = transactionTimestamp,
        legacyTransactionId = fixedRequest.transactionId,
        synchronizedTransactionId = synchronizedTransactionId,
        fixedRequest.caseloadId,
      )

      telemetryClient.trackEvent(
        "${TELEMETRY_PRISONER_PREFIX}-offender-transaction",
        mapOf(
          "legacyTransactionId" to fixedRequest.transactionId.toString(),
          "transactionId" to synchronizedTransactionId.toString(),
        ),
        null,
      )
    }

    return synchronizedTransactionId
  }

  @Transactional
  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID {
    if (request.generalLedgerEntries.isEmpty()) {
      throw IllegalArgumentException("No general ledger entries provided in the request.")
    }
    val prison = prisonService.getPrison(request.caseloadId)
      ?: prisonService.createPrison(request.caseloadId)

    val transactionTimestamp = timeConversionService.toUtcInstant(request.transactionTimestamp)
    val synchronizedTransactionId = UUID.randomUUID()

    val transactionEntries = request.generalLedgerEntries.map { glEntry ->
      val account = accountService.findGeneralLedgerAccount(
        prisonId = prison.id!!,
        accountCode = glEntry.code,
      ) ?: accountService.createGeneralLedgerAccount(
        prisonId = prison.id,
        accountCode = glEntry.code,
      )
      Triple(account.id!!, BigDecimal.valueOf(glEntry.amount), PostingType.valueOf(glEntry.postingType))
    }

    transactionService.recordTransaction(
      transactionType = request.transactionType,
      description = request.description,
      entries = transactionEntries,
      transactionTimestamp = transactionTimestamp,
      legacyTransactionId = request.transactionId,
      synchronizedTransactionId = synchronizedTransactionId,
      request.caseloadId,
    )

    telemetryClient.trackEvent(
      "${TELEMETRY_PRISONER_PREFIX}-general-ledger-transaction",
      mapOf(
        "legacyTransactionId" to request.transactionId.toString(),
        "transactionId" to synchronizedTransactionId.toString(),
      ),
      null,
    )

    return synchronizedTransactionId
  }
}
