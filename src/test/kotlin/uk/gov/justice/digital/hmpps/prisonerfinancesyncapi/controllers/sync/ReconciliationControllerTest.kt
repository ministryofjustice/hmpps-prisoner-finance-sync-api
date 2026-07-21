package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatusCode
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerService

@ExtendWith(MockitoExtension::class)
class ReconciliationControllerTest {
  @Mock
  private lateinit var generalLedgerService: GeneralLedgerService

  private lateinit var reconciliationController: ReconciliationController

  @BeforeEach
  fun setUp() {
    reconciliationController = ReconciliationController(
      generalLedgerService,
    )
  }

  @Test
  fun `should call getGLPrisonerBalances when listing the prisoners subaccount balances`() {
    val prisonNumber = "ABC123"
    val res = reconciliationController.listPrisonerSubaccountBalances(prisonNumber)

    assertThat(res.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
    verify(generalLedgerService).getGLPrisonerBalances(prisonNumber)
  }
}
