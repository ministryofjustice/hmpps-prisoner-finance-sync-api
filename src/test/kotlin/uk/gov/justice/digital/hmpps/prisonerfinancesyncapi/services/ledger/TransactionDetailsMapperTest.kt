package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountCodeLookup
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Transaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.TransactionEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountCodeLookupRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.Optional

class TransactionDetailsMapperTest {

  private lateinit var transactionDetailsMapper: TransactionDetailsMapper
  private lateinit var accountRepositoryMock: AccountRepository
  private lateinit var accountCodeLookupRepositoryMock: AccountCodeLookupRepository

  private val testId1 = 1L
  private val testId2 = 2L
  private val prisonId = "TESTPRSID"
  private val account1 = Account(
    id = testId1,
    prisonId = 10L,
    name = "Cash",
    accountType = AccountType.PRISONER,
    accountCode = 100,
    postingType = PostingType.DR,
    prisonNumber = prisonId,
  )

  private val account2 = Account(
    id = testId2,
    prisonId = 10L,
    name = "Savings",
    accountType = AccountType.PRISONER,
    accountCode = 200,
    postingType = PostingType.CR,
    prisonNumber = prisonId,
  )

  private val accountIds = listOf(testId1, testId2)
  private val accounts = listOf(account1, account2)
  private val lookupCash = AccountCodeLookup(
    accountCode = 100,
    name = "Cash",
    classification = "Asset",
    postingType = PostingType.DR,
    parentAccountCode = null,
  )

  private val lookupSavings = AccountCodeLookup(
    accountCode = 200,
    name = "Savings",
    classification = "Asset",
    postingType = PostingType.DR,
    parentAccountCode = null,
  )

  private val transaction = Transaction(
    id = 1L,
    transactionType = "Test transaction",
    description = "Test description",
    date = Timestamp.from(Instant.now()),
    prison = "TESTPRISON",
  )

  private val entry1 = TransactionEntry(
    id = 567L,
    transactionId = transaction.id!!,
    accountId = 1L,
    amount = BigDecimal("100.00"),
    entryType = PostingType.DR,
  )

  private val entry2 = TransactionEntry(
    id = 123L,
    transactionId = transaction.id!!,
    accountId = 2L,
    amount = BigDecimal("200.00"),
    entryType = PostingType.CR,
  )

  private val transactionEntries = listOf(entry1, entry2)

  @BeforeEach
  fun setUp() {
    accountRepositoryMock = mock()
    accountCodeLookupRepositoryMock = mock()
    transactionDetailsMapper = TransactionDetailsMapper(accountRepositoryMock, accountCodeLookupRepositoryMock)
  }

  @Test
  fun `mapToTransactionDetails should map entries correctly`() {
    whenever(accountRepositoryMock.findAllById(accountIds)).thenReturn(accounts)

    whenever(
      accountCodeLookupRepositoryMock.findById(100),
    ).thenReturn(Optional.of(lookupCash))
    whenever(
      accountCodeLookupRepositoryMock.findById(200),
    ).thenReturn(Optional.of(lookupSavings))

    val transactionDetails = transactionDetailsMapper.mapToTransactionDetails(transaction, transactionEntries)

    assertThat(transactionDetails.postings.count()).isEqualTo(transactionEntries.count())
    assertThat(transactionDetails.description).isEqualTo(transaction.description)
    assertThat(transactionDetails.type).isEqualTo(transaction.transactionType)

    assertThat(Timestamp.from(Instant.parse(transactionDetails.date))).isEqualTo(
      transaction.date,
    )

    for (transactionDetail in transactionDetails.postings) {
      assertThat(transactionDetail.account?.classification)
        .isNotEqualTo("Unknown")
    }
  }

  @Test
  fun `mapToTransactionDetails throw IllegalStateException when accountId is not found for transactionEntry`() {
    whenever(accountRepositoryMock.findAllById(accountIds)).thenReturn(listOf())

    assertThrows<IllegalStateException> {
      transactionDetailsMapper.mapToTransactionDetails(transaction, transactionEntries)
    }
  }

  @Test
  fun `mapToTransactionDetails should show Unknown classification when code is not in LookUp table`() {
    whenever(accountRepositoryMock.findAllById(accountIds)).thenReturn(accounts)

    whenever(
      accountCodeLookupRepositoryMock.findById(100),
    ).thenReturn(Optional.empty())
    whenever(
      accountCodeLookupRepositoryMock.findById(200),
    ).thenReturn(Optional.empty())

    val transactionDetails = transactionDetailsMapper.mapToTransactionDetails(transaction, transactionEntries)

    assertThat(transactionDetails.postings.count()).isEqualTo(transactionEntries.count())
    assertThat(transactionDetails.description).isEqualTo(transaction.description)
    assertThat(transactionDetails.type).isEqualTo(transaction.transactionType)

    assertThat(Timestamp.from(Instant.parse(transactionDetails.date))).isEqualTo(
      transaction.date,
    )

    for (transactionDetail in transactionDetails.postings) {
      assertThat(transactionDetail.account?.classification)
        .isEqualTo("Unknown")
    }
  }
}
