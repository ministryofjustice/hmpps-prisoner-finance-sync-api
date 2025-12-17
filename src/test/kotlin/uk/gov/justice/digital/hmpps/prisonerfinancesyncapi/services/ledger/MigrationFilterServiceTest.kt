package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Transaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.TransactionEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionEntryRepository
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MigrationFilterServiceTest {

  @Mock
  private lateinit var transactionEntryRepository: TransactionEntryRepository

  @InjectMocks
  private lateinit var migrationFilterService: MigrationFilterService

  private val accountId: Long = 99

  private fun makeTransaction(
    id: Long,
    type: String,
    date: Instant = Instant.now(),
    createdAt: Instant? = Instant.now(),
    prison: String = "KMI",
  ): Transaction = Transaction(
    id = id,
    uuid = UUID.randomUUID(),
    transactionType = type,
    description = "Desc",
    date = Timestamp.from(date),
    legacyTransactionId = id,
    synchronizedTransactionId = UUID.randomUUID(),
    prison = prison,
    createdAt = createdAt,
  )

  private fun makeTransactionEntry(id: Long, transactionId: Long): TransactionEntry = TransactionEntry(
    id = id,
    transactionId = transactionId,
    accountId = accountId,
    amount = BigDecimal("10.00"),
    entryType = PostingType.CR,
  )

  private fun givenTransactionEntries(vararg ids: Long) {
    val entries = ids.map { makeTransactionEntry(it, it) }
    `when`(transactionEntryRepository.findByAccountId(accountId)).thenReturn(entries)
  }

  @Nested
  @DisplayName("findLatestMigrationInfo")
  inner class FindLatestMigrationInfo {
    @Test
    fun `should return LatestMigrationInfo when findLatestMigrationInfo returns latest OB transaction`() {
      val transactionDate = Instant.now()
      val createdAt = Instant.now()

      val txn = makeTransaction(id = 1, type = "OB", createdAt = createdAt, date = transactionDate)

      givenTransactionEntries(1)

      val result = migrationFilterService.findLatestMigrationInfo(
        accountId,
        mapOf(1L to txn),
      )

      assertThat(result?.transactionDate).isEqualTo(transactionDate)
      assertThat(result?.createdAt).isEqualTo(createdAt)
    }

    @Test
    fun `should return null when findLatestMigrationInfo returns no migrationType transactions`() {
      val transactionDate = Instant.now()
      val createdAt = Instant.now()

      val txn = makeTransaction(id = 1, type = "CREDIT", createdAt = createdAt, date = transactionDate)

      givenTransactionEntries(1)

      val result = migrationFilterService.findLatestMigrationInfo(
        accountId,
        mapOf(1L to txn),
      )

      assertThat(result).isNull()
    }

    @Test
    fun `should return LatestMigrationInfo for the latest OB transaction based on CreatedAt`() {
      val oldDate = Instant.now().minus(10, ChronoUnit.DAYS)
      val newDate = Instant.now().minus(1, ChronoUnit.DAYS)

      val txn1 = makeTransaction(id = 1, type = "OB", createdAt = oldDate)
      val txn2 = makeTransaction(id = 2, type = "OB", createdAt = newDate)

      givenTransactionEntries(1, 2)

      val result = migrationFilterService.findLatestMigrationInfo(
        accountId,
        mapOf(1L to txn1, 2L to txn2),
      )

      assertThat(result).isNotNull
      assertThat(result?.createdAt).isEqualTo(newDate)
    }

    @Test
    fun `should return null when OB transaction exists but has no createdAt date`() {
      val txn = makeTransaction(id = 1, type = "OB", createdAt = null)
      givenTransactionEntries(1)

      val result = migrationFilterService.findLatestMigrationInfo(
        accountId,
        mapOf(1L to txn),
      )

      assertThat(result).isNull()
    }
  }

  @Nested
  @DisplayName("getPostMigrationTransactionEntries")
  inner class GetPostMigrationTransactionEntries {

    @Test
    fun `should return list of TransactionEntry when given a TransactionEntry list and Map of Transaction`() {
      val te = makeTransactionEntry(1, 99)

      val txn = makeTransaction(1, "CREDIT")

      val result = migrationFilterService.getPostMigrationTransactionEntries(
        1,
        listOf(te),
        mapOf(99L to txn),
      )

      assertThat(result.size).isEqualTo(1)
      assertThat(result[0].id).isEqualTo(1)
      assertThat(result[0].transactionId).isEqualTo(99)
      assertThat(result[0].amount).isEqualTo(BigDecimal("10.00"))
      assertThat(result[0].entryType).isEqualTo(PostingType.CR)
    }
  }

  @Nested
  @DisplayName("getPostMigrationTransactionEntriesForPrison")
  inner class GetPostMigrationTransactionEntriesForPrison {

    @Test
    fun `should return list of TransactionEntry when given a TransactionEntry list and Map of Transaction and correct Prison Code`() {
      val te = makeTransactionEntry(1, 99)

      val txn = makeTransaction(1, "CREDIT")

      val result = migrationFilterService.getPostMigrationTransactionEntriesForPrison(
        1,
        "KMI",
        listOf(te),
        mapOf(99L to txn),
      )

      assertThat(result.size).isEqualTo(1)
      assertThat(result[0].id).isEqualTo(1)
      assertThat(result[0].accountId).isEqualTo(99)
      assertThat(result[0].transactionId).isEqualTo(99)
      assertThat(result[0].amount).isEqualTo(BigDecimal("10.00"))
      assertThat(result[0].entryType).isEqualTo(PostingType.CR)
    }

    @Test
    fun `should return all TransactionEntries when no migration exists for the given prison`() {
      val te1 = makeTransactionEntry(id = 1, transactionId = 99)
      val te2 = makeTransactionEntry(id = 2, transactionId = 98)

      val migrationCreatedAt = Instant.parse("2023-01-01T10:00:00Z")

      // Migration exists, but in a DIFFERENT prison (MDI)
      val migrationTxn = makeTransaction(
        id = 98,
        type = "OB",
        prison = "MDI",
        createdAt = migrationCreatedAt,
        date = migrationCreatedAt
      )

      val normalTxn = makeTransaction(
        id = 99,
        type = "CREDIT",
        prison = "MDI"
      )

      val result = migrationFilterService.getPostMigrationTransactionEntriesForPrison(
        accountId = 1,
        prisonCode = "NONE", // no migration for this prison
        allEntries = listOf(te1, te2),
        allTransactions = mapOf(
          98L to migrationTxn,
          99L to normalTxn
        )
      )

      assertThat(result).hasSize(2)
      assertThat(result.map { it.id }).containsExactly(1, 2)
    }
  }
}
