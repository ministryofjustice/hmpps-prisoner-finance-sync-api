package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.exceptions

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncRequest

class SyncOffenderTransactionsException : Exception {
  constructor(request: SyncRequest, cause: Throwable) : this(cause.message, request, cause)

  constructor(message: String?, request: SyncRequest, cause: Throwable) : super(message ?: cause.message, cause) {
    this.properties = mutableMapOf(
      "requestId" to request.requestId.toString(),
      "transactionId" to request.transactionId.toString(),
      "requestType" to (request::class.simpleName ?: "UnknownRequest"),
    )

    val transactionType = when (request) {
      is SyncOffenderTransactionRequest -> request.offenderTransactions.firstOrNull()?.type
      is SyncGeneralLedgerTransactionRequest -> request.transactionType
      else -> null
    }

    if (transactionType != null) {
      this.properties["transactionType"] = transactionType
    }
  }

  val status: HttpStatusCode = HttpStatus.INTERNAL_SERVER_ERROR
  val properties: Map<String, String>
}
