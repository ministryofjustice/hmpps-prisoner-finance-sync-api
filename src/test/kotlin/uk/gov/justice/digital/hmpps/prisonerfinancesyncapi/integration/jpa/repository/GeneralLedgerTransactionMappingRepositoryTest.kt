package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.jpa.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.RepositoryTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.GeneralLedgerTransactionMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.GeneralLedgerTransactionMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
class GeneralLedgerTransactionMappingRepositoryTest(
  @Autowired private val entityManager: TestEntityManager,
  @Autowired private val glTransactionMappingRepository: GeneralLedgerTransactionMappingRepository,
) : RepositoryTestBase() {

  fun setup() {
    glTransactionMappingRepository.deleteAll()
    entityManager.flush()
  }

  @Nested
  inner class FindByCreatedAtBetween {

    @BeforeEach
    internal fun setUp() {}

    @Test
    fun `should only find mappings created on the day (midnight included)`() {
      val timeConversion = TimeConversionService()
      val firstMidnight = timeConversion.toUtcStartOfDay(LocalDate.of(2020, 1, 1))
      val firstLunchtime = timeConversion.toUtcInstant(LocalDateTime.of(2020, 1, 1, 13, 30))
      val secondMidnight = timeConversion.toUtcStartOfDay(LocalDate.of(2020, 1, 2))

      val times = listOf(firstMidnight, firstLunchtime, secondMidnight)

      val transactionMaps = times.withIndex().map { (index, it) ->
        GeneralLedgerTransactionMapping(
          legacyTransactionId = index.toLong(),
          entrySequence = 1,
          glTransactionUuid = UUID.randomUUID(),
          createdAt = it,
        )
      }

      glTransactionMappingRepository.saveAll(transactionMaps)
      entityManager.flush()

      val mappingsFromTheFirst = glTransactionMappingRepository.findAllOnDate(firstMidnight, secondMidnight)
      assertThat(mappingsFromTheFirst).hasSize(2)
      assertThat(mappingsFromTheFirst[0].createdAt).isEqualTo(firstMidnight)
      assertThat(mappingsFromTheFirst[1].createdAt).isEqualTo(firstLunchtime)
    }
  }
}
