package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.assertj.core.api.Assertions.assertThat
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
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MigrationFilterServiceTest {

  @Mock
  private lateinit var transactionEntryRepository: TransactionEntryRepository

  @InjectMocks
  private lateinit var migrationFilterService: MigrationFilterService

  @Test
  fun `should return LatestMigrationInfo when findLatestMigrationInfo returns latest OB transaction date`() {
    val accountId: Long = 3
    val transactionMap = mutableMapOf<Long, Transaction>()
    val transactionDate = Timestamp.from(Instant.now())
    val createdAt = Instant.now()

    transactionMap[1L] = Transaction(
      3,
      UUID.randomUUID(),
      "OB",
      "Description",
      date = transactionDate,
      legacyTransactionId = 3,
      synchronizedTransactionId = UUID.randomUUID(),
      prison = "KMI",
      createdAt = createdAt,
    )

    `when`(transactionEntryRepository.findByAccountId(accountId)).thenReturn(
      listOf(
        TransactionEntry(
          id = 1,
          transactionId = 3,
          accountId = accountId,
          amount = BigDecimal("3.00"),
          entryType = PostingType.CR,
        ),
      ),
    )

    val result = migrationFilterService.findLatestMigrationInfo(accountId, transactionMap)

    assertThat(result?.transactionDate).isEqualTo(transactionDate.toInstant())
    assertThat(result?.createdAt).isEqualTo(createdAt)
  }
}
