package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
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

  private val dummyPrisonId: String = "CCC"

  @BeforeEach
  fun setupGlobalDummies() {
    dummyPrison = Prison(
      id = 1,
      uuid = UUID.randomUUID(),
      code = "AAA",
    )
  }

  @Test
  fun `should find payload if found by prison code`() {
    `when`(prisonService.getPrison(Mockito.anyString())).thenReturn(dummyPrison)

    val result = prisonService.getPrison("AAA")

    assertThat(result).isNotNull()
    assertThat(result?.id).isNotNull()
  }

  @Test
  fun `should return null if not found by prison code`() {
    `when`(prisonService.getPrison(Mockito.anyString())).thenReturn(null)

    val result = prisonService.getPrison("BBB")

    assertThat(result).isNull()
  }

  @Test
  fun `should persist new prison and assign generated ID`() {
    whenever(prisonRepository.save(any())).thenAnswer { invocation ->
      val prisonResult = invocation.getArgument<Prison>(0)
      prisonResult.copy(id = 1L)
    }

    val savedPrison = prisonService.createPrison(dummyPrisonId)

    assertEquals(1L, savedPrison.id)
    assertEquals(dummyPrisonId, savedPrison.code)
    verify(prisonRepository).save(any())
  }
}
