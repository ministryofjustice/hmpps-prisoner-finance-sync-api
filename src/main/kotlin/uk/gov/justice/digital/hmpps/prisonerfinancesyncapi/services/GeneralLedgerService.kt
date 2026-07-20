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
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LegacyTransactionFixService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.util.UUID

@Service("generalLedgerService")
class GeneralLedgerService(
  private val generalLedgerApiClient: GeneralLedgerApiClient,
  private val accountMapping: LedgerAccountMappingService,
  private val telemetryClient: TelemetryClient,
  private val timeConversionService: TimeConversionService,
  private val idempotencyService: GeneralLedgerIdempotencyService,
  private val generalLedgerTransactionMappingRepository: GeneralLedgerTransactionMappingRepository,
  private val generalLedgerAccountResolver: GeneralLedgerAccountResolver,
  private val legacyTransactionFixService: LegacyTransactionFixService,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(GeneralLedgerService::class.java)
  }

  data class SyncOffenderTransactionToGeneralLedgerResponse(
    val previouslyMappedTransactionEntries: List<GeneralLedgerTransactionMapping>,
    val unsuccessfullyMappedTransactionEntries: List<OffenderTransaction>,
    val successfullyMappedTransactionEntries: List<GeneralLedgerTransactionMapping>,
  )

  private fun sendTransactionToGl(
    transaction: OffenderTransaction,
    fixedRequest: SyncOffenderTransactionRequest,
    requestCache: InMemoryAccountCache,
  ): UUID {
    val offenderId = transaction.offenderDisplayId
    val postings = transaction.generalLedgerEntries.map { entry ->

      val subAccountUuid = generalLedgerAccountResolver.resolveSubAccount(
        prisonId = fixedRequest.caseloadId,
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
      timestamp = timeConversionService.toUtcInstant(fixedRequest.transactionTimestamp),
      amount = transaction.amount.toPence(),
      postings = postings,
    )

    return generalLedgerApiClient.postTransaction(
      glRequest,
      idempotencyService.genTransactionIdempotencyKey(
        fixedRequest.transactionId,
        transaction.entrySequence,
      ),
      fixedRequest.transactionId,
    )
  }

  fun syncOffenderTransaction(request: SyncOffenderTransactionRequest): SyncOffenderTransactionToGeneralLedgerResponse {
    val fixedRequest = legacyTransactionFixService.fixLegacyTransactions(request)

    if(fixedRequest.offenderTransactions.isEmpty()) {
      log.error("Error: No offender transactions found in request ${request.transactionId}")
      throw CustomException("No offender transactions found in request", status = HttpStatus.BAD_REQUEST)
    }

    val requestCache = InMemoryAccountCache()

    val previouslyMappedTransactionEntries = generalLedgerTransactionMappingRepository
      .findGeneralLedgerTransactionMappingByLegacyTransactionId(fixedRequest.transactionId).associateBy { it.entrySequence }

    val unsuccessfullyMappedTransactionEntries = mutableListOf<OffenderTransaction>()
    val successfullyMappedTransactionEntries = mutableListOf<GeneralLedgerTransactionMapping>()

    for (transaction in fixedRequest.offenderTransactions) {
      if (transaction.entrySequence in previouslyMappedTransactionEntries) {
        log.info(
          "For NOMIS Transaction ID=${fixedRequest.transactionId} Skipping transaction with entry sequence ${transaction.entrySequence} as it has already been mapped and processed in the " +
            "general ledger",
        )
        continue
      }
      try {
        val transactionGLUUID = sendTransactionToGl(
          transaction,
          fixedRequest,
          requestCache,
        )

        val transactionMapping = GeneralLedgerTransactionMapping(
          legacyTransactionId = fixedRequest.transactionId,
          entrySequence = transaction.entrySequence,
          glTransactionUuid = transactionGLUUID,
          createdAt = timeConversionService.toUtcInstant(fixedRequest.createdAt),
          caseloadId = fixedRequest.caseloadId,
          transactionType = transaction.type,
        )

        generalLedgerTransactionMappingRepository.save(transactionMapping)

        successfullyMappedTransactionEntries.add(transactionMapping)
      } catch (e: Exception) {
        unsuccessfullyMappedTransactionEntries.add(
          transaction,
        )

        val properties = mapOf(
          "requestId" to fixedRequest.requestId.toString(),
          "transactionId" to fixedRequest.transactionId.toString(),
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

    return SyncOffenderTransactionToGeneralLedgerResponse(
      previouslyMappedTransactionEntries = previouslyMappedTransactionEntries.values.toList(),
      unsuccessfullyMappedTransactionEntries = unsuccessfullyMappedTransactionEntries,
      successfullyMappedTransactionEntries = successfullyMappedTransactionEntries,
    )
  }

  private fun logRequestAsError(properties: Map<String, String>, exception: Exception) {
    log.error("Failed to forward transaction to General Ledger $properties", exception)

    telemetryClient.trackException(exception, properties, null)
  }

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
    val transactionMappings = generalLedgerTransactionMappingRepository.findGeneralLedgerTransactionMappingByLegacyTransactionId(legacyTransactionId)
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
