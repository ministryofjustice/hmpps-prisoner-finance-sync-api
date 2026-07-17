package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.GeneralLedgerTransactionMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.GeneralLedgerTransactionMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SearchPostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SearchTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceForReconciliation
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.util.UUID

@Service("generalLedgerService")
class GeneralLedgerService(
  private val generalLedgerApiClient: GeneralLedgerApiClient,
  private val accountMapping: LedgerAccountMappingService,
  private val telemetryClient: TelemetryClient,
  private val timeConversionService: TimeConversionService,
  private val idempotencyService: GeneralLedgerIdempotencyService,
  private val ledgerTransactionMappingRepository: GeneralLedgerTransactionMappingRepository,
  private val generalLedgerAccountResolver: GeneralLedgerAccountResolver,
) : LedgerService {

  private companion object {
    private val log = LoggerFactory.getLogger(GeneralLedgerService::class.java)
  }

  override fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): List<UUID> {
    val transactionGLUUIDs = mutableListOf<UUID>()
    val requestCache = InMemoryAccountCache()

    request.offenderTransactions.forEach { transaction ->

      val offenderId = transaction.offenderDisplayId
      val postings = transaction.generalLedgerEntries.map { entry ->

        val subAccountUuid = generalLedgerAccountResolver.resolveSubAccount(
          prisonId = request.caseloadId,
          offenderId = offenderId,
          accountCode = entry.code,
          transactionType = transaction.type,
          parentCache = requestCache,
        )

        return@map CreatePostingRequest(
          subAccountUuid,
          type = CreatePostingRequest.Type.valueOf(entry.postingType),
          entrySequence = entry.entrySequence.toLong(),
          amount = entry.amount.toPence(),
        )
      }

      val glRequest = CreateTransactionRequest(
        reference = transaction.reference ?: "",
        entrySequence = transaction.entrySequence.toLong(),
        description = transaction.description,
        timestamp = timeConversionService.toUtcInstant(request.transactionTimestamp),
        amount = transaction.amount.toPence(),
        postings = postings,
      )

      try {
        val transactionGLUUID = generalLedgerApiClient.postTransaction(
          glRequest,
          idempotencyService.genTransactionIdempotencyKey(
            request.transactionId,
            transaction.entrySequence,
          ),
          request.transactionId,
        )

        ledgerTransactionMappingRepository.save(
          GeneralLedgerTransactionMapping(
            legacyTransactionId = request.transactionId,
            entrySequence = transaction.entrySequence,
            glTransactionUuid = transactionGLUUID,
            createdAt = timeConversionService.toUtcInstant(request.createdAt),
            caseloadId = request.caseloadId,
            transactionType = transaction.type,
          ),
        )
        transactionGLUUIDs.add(transactionGLUUID)
      } catch (e: Exception) {
        val properties = mapOf(
          "requestId" to request.requestId.toString(),
          "transactionId" to request.transactionId.toString(),
          "transactionType" to transaction.type,
          "entrySequence" to transaction.entrySequence.toString(),
          "exceptionMessage" to if (e is WebClientResponseException) {
            "${e.responseBodyAsString}\n${e.message}"
          } else {
            e.message.toString()
          },
        )

        logRequestAsError(properties, e)
      }
    }

    if (request.offenderTransactions.isEmpty() || transactionGLUUIDs.count() != request.offenderTransactions.count()) {
      val illegalStateException = IllegalStateException("Not All General Ledger Transaction were resolved")

      val properties = mapOf(
        "requestId" to request.requestId.toString(),
        "transactionId" to request.transactionId.toString(),
        "glTransactionsResolved" to transactionGLUUIDs.toString(),
      )

      logRequestAsError(properties, illegalStateException)

      throw illegalStateException
    }

    return transactionGLUUIDs
  }

  private fun logRequestAsError(properties: Map<String, String>, exception: Exception) {
    log.error("Failed to forward transaction to General Ledger $properties", exception)

    telemetryClient.trackException(exception, properties, null)
  }

  override fun syncGeneralLedgerTransaction(request: SyncGeneralLedgerTransactionRequest): UUID = throw NotImplementedError("Syncing General Ledger Transactions is not yet supported in the new General Ledger Service")

  fun getGLPrisonerBalances(prisonNumber: String): Map<String, SubAccountBalanceForReconciliation> {
    val parentAccount = generalLedgerApiClient.findAccountByReference(prisonNumber)

    if (parentAccount == null) {
      throw CustomException("No General Ledger account found for prisoner $prisonNumber", status = HttpStatus.NOT_FOUND)
    }

    val subAccounts = mutableMapOf<String, SubAccountBalanceForReconciliation>()
    for (account in parentAccount.subAccounts) {
      val subAccountBalance = generalLedgerApiClient.findSubAccountBalanceByAccountId(account.id)
      if (subAccountBalance == null) {
        log.error("No balance found for account ${account.id} but it was in the parent subaccounts list")
        continue
      }
      val accountCode = accountMapping.mapSubAccountPrisonerReferenceToNOMIS(account.reference).toString()
      subAccounts[accountCode] = SubAccountBalanceForReconciliation.fromSubAccountBalanceResponse(subAccountBalance)
    }

    return subAccounts
  }

  private fun isSubAccountTransfer(glTransaction: SearchTransactionResponse): Boolean = glTransaction.postings.all { it.accountType == SearchPostingResponse.AccountType.PRISONER }

  private fun makeOffenderTransaction(
    glTransaction: SearchTransactionResponse,
    posting: SearchPostingResponse,
    glTransactionMapping: GeneralLedgerTransactionMapping,
    generalLedgerEntries: List<GeneralLedgerEntry>,
    entrySequence: Int,
  ) = OffenderTransaction(
    entrySequence = entrySequence,
    offenderId = null,
    offenderDisplayId = posting.accountReference,
    offenderBookingId = null,
    subAccountType = accountMapping.mapGlPrisonerSubAccountReferenceToNomisReference(
      posting.subAccountReference,
    ),
    postingType = posting.type.name,
    type = glTransactionMapping.transactionType ?: "",
    description = glTransaction.description,
    amount = glTransaction.amount.toBigDecimal().movePointLeft(2),
    reference = glTransaction.reference,
    generalLedgerEntries = generalLedgerEntries,
  )

  private fun mapGlTransactionToSyncOffenderTransactionResponse(
    glTransaction: SearchTransactionResponse,
    glTransactionMapping: GeneralLedgerTransactionMapping,
  ): List<OffenderTransaction> {
    if (isSubAccountTransfer(glTransaction)) {
      val (firstPosting, secondPosting) = glTransaction.postings.sortedBy { it.entrySequence }

      return listOf(
        makeOffenderTransaction(
          glTransaction = glTransaction,
          posting = firstPosting,
          glTransactionMapping = glTransactionMapping,
          generalLedgerEntries = glTransaction.postings.map { GeneralLedgerEntry.fromGeneralLedgerPostingResponse(it) },
          entrySequence = glTransaction.entrySequence.toInt(),
        ),
        makeOffenderTransaction(
          glTransaction = glTransaction,
          posting = secondPosting,
          glTransactionMapping = glTransactionMapping,
          generalLedgerEntries = emptyList(),
          entrySequence = glTransaction.entrySequence.toInt() + 1,
        ),
      )
    } else {
      val prisonerPosting = glTransaction.postings.first { it.accountType == SearchPostingResponse.AccountType.PRISONER }

      return listOf(
        makeOffenderTransaction(
          glTransaction = glTransaction,
          posting = prisonerPosting,
          glTransactionMapping = glTransactionMapping,
          generalLedgerEntries = glTransaction.postings.map { GeneralLedgerEntry.fromGeneralLedgerPostingResponse(it) },
          entrySequence = glTransaction.entrySequence.toInt(),
        ),
      )
    }
  }

  fun retrieveNOMISTransactionByLegacyTransactionId(legacyTransactionId: Long): SyncOffenderTransactionResponse {
    val transactionMappings = ledgerTransactionMappingRepository.findGeneralLedgerTransactionMappingByLegacyTransactionId(legacyTransactionId)
    if (transactionMappings.isEmpty()) {
      throw CustomException("No mapping found for $legacyTransactionId", status = HttpStatus.NOT_FOUND)
    }
    val transactionMappingByGlTransactionUUID = transactionMappings.associateBy { it.glTransactionUuid }

    val glTransactions = generalLedgerApiClient.searchTransactions(
      transactionMappings.map { it.glTransactionUuid },
      pageNumber = 1,
      pageSize = 999,
    ).content

    if (glTransactions.isEmpty()) {
      throw CustomException("No gl transaction found for gl $legacyTransactionId", status = HttpStatus.NOT_FOUND)
    }

    return SyncOffenderTransactionResponse(
      synchronizedTransactionId = null, // id reference the old internal ledger, not required
      legacyTransactionId = legacyTransactionId,
      caseloadId = transactionMappings.first().caseloadId ?: "",
      transactionTimestamp = timeConversionService.toLocalDateTime(glTransactions.first().timestamp),
      createdAt = timeConversionService.toLocalDateTime(glTransactions.first().createdAt),
      createdBy = "",
      createdByDisplayName = "",
      lastModifiedAt = null,
      lastModifiedBy = "",
      lastModifiedByDisplayName = "",
      transactions = glTransactions.flatMap {
        mapGlTransactionToSyncOffenderTransactionResponse(
          it,
          transactionMappingByGlTransactionUUID.getValue(it.id),
        )
      },
    )
  }
}
