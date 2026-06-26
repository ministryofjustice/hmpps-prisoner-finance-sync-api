package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
  fun `should call reconcile prisoner when listing prisoners by establishment`() {
    val prisonNumber = "ABC123"
    val res = reconciliationController.listPrisonerBalancesByEstablishment(prisonNumber)

    assertThat(res.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
    verify(generalLedgerService).reconcilePrisoner(prisonNumber)
  }

  @Disabled("Not yet implemented")
  @Test
  fun `should call ledger query service when listing prison balances`() {
    val prisonId = "PRI"
    val res = reconciliationController.listGeneralLedgerBalances(prisonId)

    assertThat(res.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
    // verify(ledgerQueryService).listGeneralLedgerBalances(prisonId)
  }
}
