package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Transaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.TransactionEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionEntryRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.AccountService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.TransactionService
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class PrisonerAccountMergeServiceTest {

  @Mock
  lateinit var accountRepository: AccountRepository

  @Mock
  lateinit var transactionRepository: TransactionRepository

  @Mock
  lateinit var transactionEntryRepository: TransactionEntryRepository

  @Mock
  lateinit var accountService: AccountService

  @Mock
  lateinit var transactionService: TransactionService

  @InjectMocks
  lateinit var service: PrisonerAccountMergeService

  private val fromPrisoner = "A1111AA"
  private val toPrisoner = "B2222BB"

  @Nested
  @DisplayName("consolidateAccounts")
  inner class ConsolidateAccounts {

    @Test
    fun `should transfer balance by reversing source and reinstating target transactions`() {
      val fromAccount = createPrisonerAccount(10L, fromPrisoner)
      val toAccount = createPrisonerAccount(20L, toPrisoner)
      val glAccount = createGlAccount(99L)

      val transaction = Transaction(id = 100L, transactionType = "CANTEEN", description = "Canteen Spends", date = Timestamp.from(Instant.now()), prison = "MDI")

      // Prisoner DR £5, GL CR £5
      val prisonerTransactionEntry = TransactionEntry(id = 1, transactionId = transaction.id!!, accountId = fromAccount.id!!, amount = BigDecimal("5.00"), entryType = PostingType.DR)
      val glTransactionEntry = TransactionEntry(id = 2, transactionId = transaction.id, accountId = glAccount.id!!, amount = BigDecimal("5.00"), entryType = PostingType.CR)

      whenever(accountRepository.findByPrisonNumber(fromPrisoner)).thenReturn(listOf(fromAccount))
      whenever(accountRepository.findByPrisonNumberAndAccountCode(toPrisoner, 2102)).thenReturn(toAccount)

      whenever(transactionEntryRepository.findByAccountId(fromAccount.id)).thenReturn(listOf(prisonerTransactionEntry))
      whenever(transactionEntryRepository.findByTransactionId(100L)).thenReturn(listOf(prisonerTransactionEntry, glTransactionEntry))
      whenever(transactionRepository.findById(100L)).thenReturn(Optional.of(transaction))

      service.consolidateAccounts(fromPrisoner, toPrisoner)

      val descriptionCaptor = argumentCaptor<String>()
      val entriesCaptor = argumentCaptor<List<Triple<Long, BigDecimal, PostingType>>>()

      verify(transactionService, times(2)).recordTransaction(
        transactionType = eq("CANTEEN"),
        description = descriptionCaptor.capture(),
        entries = entriesCaptor.capture(),
        transactionTimestamp = anyOrNull(),
        legacyTransactionId = anyOrNull(),
        synchronizedTransactionId = anyOrNull(),
        prison = eq("MDI"),
        createdAt = anyOrNull(),
      )

      val descriptions = descriptionCaptor.allValues
      val capturedEntries = entriesCaptor.allValues

      // Reversal Check
      assertThat(descriptions[0]).contains("REVERSE TRANSACTION 100: Canteen Spends")
      val reversal = capturedEntries[0]
      assertThat(reversal).contains(
        Triple(fromAccount.id, BigDecimal("5.00"), PostingType.CR),
        Triple(glAccount.id, BigDecimal("5.00"), PostingType.DR),
      )

      // Transfer Check
      assertThat(descriptions[1]).contains("MERGE TRANSFER 100: Canteen Spends")
      val transfer = capturedEntries[1]
      assertThat(transfer).contains(
        Triple(toAccount.id!!, BigDecimal("5.00"), PostingType.DR),
        Triple(glAccount.id, BigDecimal("5.00"), PostingType.CR),
      )
    }

    @Test
    fun `should create target account if missing`() {
      val fromAccount = createPrisonerAccount(10L, fromPrisoner)
      val newAccount = createPrisonerAccount(20L, toPrisoner)

      whenever(accountRepository.findByPrisonNumber(fromPrisoner)).thenReturn(listOf(fromAccount))
      whenever(accountRepository.findByPrisonNumberAndAccountCode(toPrisoner, 2102)).thenReturn(null)
      whenever(transactionEntryRepository.findByAccountId(10L)).thenReturn(emptyList())

      whenever(
        accountService.createAccount(
          anyOrNull(),
          any(),
          any(),
          eq(2102),
          any(),
          eq(toPrisoner),
          anyOrNull(),
        ),
      ).thenReturn(newAccount)

      service.consolidateAccounts(fromPrisoner, toPrisoner)

      verify(accountService).createAccount(
        prisonId = eq(999L),
        name = eq("$toPrisoner - Spends"),
        accountType = eq(AccountType.PRISONER),
        accountCode = eq(2102),
        postingType = any(),
        prisonNumber = eq(toPrisoner),
        subAccountType = eq("Spends"),
      )
    }
  }

  @Test
  fun `should remap opening balance transaction type (OB) to OT during reinstatement`() {
    val fromAccount = createPrisonerAccount(10L, fromPrisoner)
    val toAccount = createPrisonerAccount(20L, toPrisoner)
    val transaction = Transaction(id = 500L, transactionType = "OB", description = "Migration", date = Timestamp.from(Instant.now()), prison = "MDI")
    val entry = TransactionEntry(id = 1, transactionId = 500L, accountId = 10L, amount = BigDecimal("10.00"), entryType = PostingType.DR)

    whenever(accountRepository.findByPrisonNumber(fromPrisoner)).thenReturn(listOf(fromAccount))
    whenever(accountRepository.findByPrisonNumberAndAccountCode(toPrisoner, 2102)).thenReturn(toAccount)
    whenever(transactionEntryRepository.findByAccountId(10L)).thenReturn(listOf(entry))
    whenever(transactionEntryRepository.findByTransactionId(500L)).thenReturn(listOf(entry))
    whenever(transactionRepository.findById(500L)).thenReturn(Optional.of(transaction))

    service.consolidateAccounts(fromPrisoner, toPrisoner)

    val typeCaptor = argumentCaptor<String>()
    val descriptionCaptor = argumentCaptor<String>()

    verify(transactionService, times(2)).recordTransaction(
      transactionType = typeCaptor.capture(),
      description = descriptionCaptor.capture(),
      entries = any(),
      transactionTimestamp = anyOrNull(),
      legacyTransactionId = anyOrNull(),
      synchronizedTransactionId = anyOrNull(),
      prison = eq("MDI"),
      createdAt = anyOrNull(),
    )

    val types = typeCaptor.allValues
    val descriptions = descriptionCaptor.allValues

    assertThat(types[0]).isEqualTo("OT")
    assertThat(descriptions[0]).contains("REVERSE TRANSACTION")

    assertThat(types[1]).isEqualTo("OT")
    assertThat(descriptions[1]).contains("MERGE TRANSFER")
  }

  private fun createPrisonerAccount(id: Long, prisonNumber: String): Account = Account(
    id = id,
    prisonId = 999L,
    accountCode = 2102,
    name = "Prisoner Spends",
    accountType = AccountType.PRISONER,
    prisonNumber = prisonNumber,
    subAccountType = "Spends",
    postingType = PostingType.DR,
  )

  private fun createGlAccount(id: Long): Account = Account(
    id = id,
    prisonId = 999L,
    accountCode = 9999,
    name = "General Ledger",
    accountType = AccountType.GENERAL_LEDGER,
    prisonNumber = null,
    subAccountType = null,
    postingType = PostingType.CR,
  )
}
