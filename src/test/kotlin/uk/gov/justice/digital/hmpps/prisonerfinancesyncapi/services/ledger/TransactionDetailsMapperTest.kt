package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Optional

class TransactionDetailsMapperTest {

  private lateinit var transactionDetailsMapper: TransactionDetailsMapper
  private lateinit var accountRepositoryMock: AccountRepository
  private lateinit var accountCodeLookupRepositoryMock: AccountCodeLookupRepository

  @BeforeEach
  fun setUp() {
    accountRepositoryMock = mock()
    accountCodeLookupRepositoryMock = mock()
    transactionDetailsMapper = TransactionDetailsMapper(accountRepositoryMock, accountCodeLookupRepositoryMock)
  }

  private fun timeStampToString(timestamp: Timestamp, format : String = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"): String =
    timestamp.toInstant()
    .atOffset(ZoneOffset.UTC)
    .format(
      DateTimeFormatter.ofPattern(format),
    )

  @Test
  fun `mapToTransactionDetails should map entries correctly`() {
    val testId1 = 1L
    val testId2 = 2L
    val accountIds = listOf(testId1, testId2)
    val prisonId = "TESTPRSID"
    val account1 = Account(
      id = testId1,
      prisonId = 10L,
      name = "Cash",
      accountType = AccountType.PRISONER,
      accountCode = 100,
      postingType = PostingType.DR,
      prisonNumber = prisonId,
    )

    val account2 = Account(
      id = testId2,
      prisonId = 10L,
      name = "Savings",
      accountType = AccountType.PRISONER,
      accountCode = 200,
      postingType = PostingType.CR,
      prisonNumber = prisonId,
    )

    val accounts = listOf(account1, account2)

    whenever(accountRepositoryMock.findAllById(accountIds)).thenReturn(accounts)

    val lookupCash = AccountCodeLookup(
      accountCode = 100,
      name = "Cash",
      classification = "Asset",
      postingType = PostingType.DR,
      parentAccountCode = null,
    )

    val lookupSavings = AccountCodeLookup(
      accountCode = 200,
      name = "Savings",
      classification = "Asset",
      postingType = PostingType.DR,
      parentAccountCode = null,
    )

    whenever(
      accountCodeLookupRepositoryMock.findById(100),
    ).thenReturn(Optional.of(lookupCash))
    whenever(
      accountCodeLookupRepositoryMock.findById(200),
    ).thenReturn(Optional.of(lookupSavings))

    val transaction = Transaction(
      id = 1L,
      transactionType = "Test transaction",
      description = "Test description",
      date = Timestamp.from(Instant.now()),
      prison = "TESTPRISON",
    )

    val entry1 = TransactionEntry(
      id = 567L,
      transactionId = transaction.id!!,
      accountId = 1L,
      amount = BigDecimal("100.00"),
      entryType = PostingType.DR,
    )

    val entry2 = TransactionEntry(
      id = 123L,
      transactionId = transaction.id,
      accountId = 2L,
      amount = BigDecimal("200.00"),
      entryType = PostingType.CR,
    )

    val transactionEntries = listOf(entry1, entry2)

    val transactionDetails = transactionDetailsMapper.mapToTransactionDetails(transaction, transactionEntries)

    assertThat(transactionDetails.postings.count()).isEqualTo(transactionEntries.count())
    assertThat(transactionDetails.description).isEqualTo(transaction.description)
    assertThat(transactionDetails.type).isEqualTo(transaction.transactionType)

    assertThat(transactionDetails.date).isEqualTo(
      timeStampToString(transaction.date),
    )
  }
}
