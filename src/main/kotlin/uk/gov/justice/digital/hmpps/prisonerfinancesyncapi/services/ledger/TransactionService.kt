package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.events.TransactionRecordedEvent
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Transaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.TransactionEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionEntryRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionRepository
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Service
open class TransactionService(
  private val transactionRepository: TransactionRepository,
  private val transactionEntryRepository: TransactionEntryRepository,
  private val accountRepository: AccountRepository,
  private val eventPublisher: ApplicationEventPublisher,
) {

  @Transactional
  open fun recordTransaction(
    transactionType: String,
    description: String,
    entries: List<Triple<Long, BigDecimal, PostingType>>,
    transactionTimestamp: Instant? = null,
    legacyTransactionId: Long? = null,
    synchronizedTransactionId: UUID? = null,
    prison: String,
    createdAt: Instant? = null,
  ): Transaction {
    if (entries.isEmpty()) {
      throw IllegalArgumentException("Transaction must have at least one entry.")
    }

    val totalDebits = entries.filter { it.third == PostingType.DR }.sumOf { it.second }
    val totalCredits = entries.filter { it.third == PostingType.CR }.sumOf { it.second }

    if (totalDebits.compareTo(totalCredits) != 0) {
      throw IllegalArgumentException("Transaction is not balanced. Total debits and credits must be equal.")
    }

    val timestamp = transactionTimestamp?.let { Timestamp.from(it) } ?: Timestamp(System.currentTimeMillis())

    val transaction = Transaction(
      transactionType = transactionType,
      description = description,
      date = timestamp,
      legacyTransactionId = legacyTransactionId,
      synchronizedTransactionId = synchronizedTransactionId,
      prison = prison,
      createdAt = createdAt ?: Instant.now(),
    )
    val savedTransaction = transactionRepository.save(transaction)
    val savedEntries = mutableListOf<TransactionEntry>()

    entries.forEach { (accountId, amount, entryType) ->
      accountRepository.findById(accountId)
        .orElseThrow { IllegalStateException("Account not found with ID: $accountId") }

      val entry = TransactionEntry(
        transactionId = savedTransaction.id!!,
        accountId = accountId,
        amount = amount.abs(),
        entryType = entryType,
      )
      savedEntries.add(transactionEntryRepository.save(entry))
    }

    eventPublisher.publishEvent(TransactionRecordedEvent(savedEntries))

    return savedTransaction
  }
}
