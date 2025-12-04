package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Prison
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.PrisonRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.AccountService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.PrisonService
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@DisplayName("PrisonServiceTest")
class PrisonServiceTest {

  @Mock
  lateinit var prisonRepository: PrisonRepository

  @Mock
  lateinit var accountService: AccountService

  @InjectMocks
  lateinit var prisonService: PrisonService

  private lateinit var dummyPrison: Prison

  @BeforeEach
  fun setupGlobalDummies() {
    dummyPrison = Prison(
      id = 1,
      uuid = UUID.randomUUID(),
      code = "AAA",
    )
  }

  @Nested
  @DisplayName("getPrison")
  inner class GetPrison {
    @Test
    fun `should return prison when found by prison code`() {
      `when`(prisonRepository.findByCode(dummyPrison.code)).thenReturn(dummyPrison)

      val prison = prisonService.getPrison(dummyPrison.code)

      assertThat(prison).isEqualTo(dummyPrison)
      verify(prisonRepository).findByCode(dummyPrison.code)
    }

    @Test
    fun `should return null when not found by prison code`() {
      `when`(prisonRepository.findByCode(dummyPrison.code)).thenReturn(null)

      val result = prisonService.getPrison(dummyPrison.code)

      assertThat(result).isNull()
    }
  }

  @Nested
  @DisplayName("createPrison")
  inner class CreatePrison {

    @Test
    fun `should persist new prison and assign generated ID`() {
      val savedPrisonId = 999L
      val dummyPrisonId: String = "CCC"

      whenever(prisonRepository.save(any())).thenAnswer { invocation ->
        val prisonResult = invocation.getArgument<Prison>(0)
        prisonResult.copy(id = savedPrisonId)
      }

      val savedPrison = prisonService.createPrison(dummyPrisonId)

      assertEquals(savedPrisonId, savedPrison.id)
      assertEquals(dummyPrisonId, savedPrison.code)

      verify(accountService).createGeneralLedgerAccount(savedPrisonId, 2101)
      verify(accountService).createGeneralLedgerAccount(savedPrisonId, 2102)
      verify(accountService).createGeneralLedgerAccount(savedPrisonId, 2103)

      verifyNoMoreInteractions(accountService)

      verify(prisonRepository).save(any())
    }
  }
}
