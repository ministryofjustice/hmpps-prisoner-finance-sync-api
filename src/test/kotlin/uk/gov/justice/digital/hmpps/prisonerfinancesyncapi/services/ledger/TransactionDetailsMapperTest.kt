package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountCodeLookup
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Transaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.TransactionEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountCodeLookupRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.TransactionDetails
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.Optional
import kotlin.random.Random

@ExtendWith(MockitoExtension::class)
class TransactionDetailsMapperTest {

  @Mock
  private lateinit var accountRepositoryMock: AccountRepository

  @Mock
  private lateinit var accountCodeLookupRepositoryMock: AccountCodeLookupRepository

  @InjectMocks
  private lateinit var transactionDetailsMapper: TransactionDetailsMapper

  @Nested
  @DisplayName("mapToTransactionDetails")
  inner class MapToTransactionDetails {

    private val testId1 = 1L
    private val testId2 = 2L
    private val prisonId = "TESTPRSID"
    private val accountCashName = "Cash"
    private val accountSavingsName = "Savings"
    private val accountCashCode = 100
    private val accountSavingsCode = 200

    private val accountCash = Account(
      id = testId1,
      prisonId = 10L,
      name = accountCashName,
      accountType = AccountType.PRISONER,
      accountCode = accountCashCode,
      postingType = PostingType.DR,
      prisonNumber = prisonId,
    )

    private val accountSavings = Account(
      id = testId2,
      prisonId = 10L,
      name = accountSavingsName,
      accountType = AccountType.PRISONER,
      accountCode = accountSavingsCode,
      postingType = PostingType.CR,
      prisonNumber = prisonId,
    )

    private val accountIds = listOf(testId1, testId2)
    private val accounts = listOf(accountCash, accountSavings)

    private fun createAccountCodeLookup(accountCode: Int, name: String) = AccountCodeLookup(
      accountCode = accountCode,
      name = name,
      classification = "Asset",
      postingType = PostingType.DR,
      parentAccountCode = null,
    )

    private val lookupCash = createAccountCodeLookup(accountCashCode, accountCashName)

    private val lookupSavings = createAccountCodeLookup(accountSavingsCode, accountSavingsName)

    private val transaction = Transaction(
      id = 1L,
      transactionType = "Test transaction",
      description = "Test description",
      date = Timestamp.from(Instant.now()),
      prison = "TESTPRISON",
    )

    private fun createTransactionEntry(accountId: Long) = TransactionEntry(
      id = Random.nextLong(1, 10_000_000),
      transactionId = transaction.id!!,
      accountId = accountId,
      amount = BigDecimal("100.00"),
      entryType = PostingType.DR,
    )

    private val entry1 = createTransactionEntry(1L)

    private val entry2 = createTransactionEntry(2L)

    private val transactionEntries = listOf(entry1, entry2)

    private fun assertThatTransactionDetailsDataMatches(transactionDetails: TransactionDetails) {
      assertThat(transactionDetails.postings.count()).isEqualTo(transactionEntries.count())
      assertThat(transactionDetails.postings).extracting("amount").containsOnly(BigDecimal("100.00"))
      assertThat(transactionDetails.postings[0].account?.code).isEqualTo(accountCashCode)
      assertThat(transactionDetails.postings[0].account?.name).isEqualTo(accountCashName)
      assertThat(transactionDetails.postings[1].account?.code).isEqualTo(accountSavingsCode)
      assertThat(transactionDetails.postings[1].account?.name).isEqualTo(accountSavingsName)
      assertThat(transactionDetails.postings).extracting("account.postingType").containsOnly(PostingType.DR)
      assertThat(transactionDetails.postings).extracting("account.prisoner").containsOnly(prisonId)
      assertThat(transactionDetails.postings).extracting("account.prison").containsOnly(transaction.prison)
      assertThat(transactionDetails.postings).extracting("account.transactionType").containsOnly(transaction.transactionType)
      assertThat(transactionDetails.postings).extracting("account.transactionDescription").containsOnly(transaction.description)

      assertThat(transactionDetails.description).isEqualTo(transaction.description)
      assertThat(transactionDetails.type).isEqualTo(transaction.transactionType)
      assertThat(Timestamp.from(Instant.parse(transactionDetails.date))).isEqualTo(
        transaction.date,
      )
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

      assertThatTransactionDetailsDataMatches(transactionDetails)
      assertThat(transactionDetails.postings).extracting("account.classification").containsOnly("Asset")
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

      assertThatTransactionDetailsDataMatches(transactionDetails)
      assertThat(transactionDetails.postings).extracting("account.classification").containsOnly("Unknown")
    }
  }
}
