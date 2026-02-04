package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatusCode
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.DualWriteLedgerService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService

@ExtendWith(MockitoExtension::class)
class ReconciliationControllerTest {

  @Mock
  private lateinit var ledgerQueryService: LedgerQueryService

  @Mock
  private lateinit var dualWriteLedgerService: DualWriteLedgerService

  @InjectMocks
  private lateinit var reconciliationController: ReconciliationController

  @Test
  fun `should call duelWriteLedgerService`() {
    val prisonNumber = "ABC123"
    val res = reconciliationController.listPrisonerBalancesByEstablishment(prisonNumber)

    assertThat(res.statusCode).isEqualTo(HttpStatusCode.valueOf(200))
    verify(dualWriteLedgerService).reconcilePrisoner(prisonNumber)
  }
}
