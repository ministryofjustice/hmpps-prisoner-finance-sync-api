package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DualReadLedgerServiceTest {

  @Mock
  private lateinit var generalLedger: GeneralLedgerService

  @Mock
  private lateinit var ledgerQueryService: LedgerQueryService

  @Mock
  private lateinit var generalLedgerForwarder: GeneralLedgerForwarder

  private lateinit var dualReadLedgerService: DualReadLedgerService

  @BeforeEach
  fun setup() {
    dualReadLedgerService = DualReadLedgerService(generalLedger, ledgerQueryService, generalLedgerForwarder)
  }

  val prisonNumber = "A1234AB"

  @Test
  fun `should call both internalLedger and General Ledger when reconciling prisoner`() {
    val lambdaCaptor = argumentCaptor<() -> UUID>()
    val expectedResult = mock<List<PrisonerEstablishmentBalanceDetails>>()
    whenever(ledgerQueryService.listPrisonerBalancesByEstablishment(prisonNumber)).thenReturn(expectedResult)

    val res = dualReadLedgerService.reconcilePrisoner(prisonNumber)

    verify(generalLedgerForwarder).executeIfEnabled(
      eq("Failed to reconcile prisoner $prisonNumber to General Ledger"),
      eq(prisonNumber),
      lambdaCaptor.capture(),
    )

    verifyNoInteractions(generalLedger)
    lambdaCaptor.firstValue.invoke() // Switch service is a mock. Lambda requires a manual trigger to check what's called
    verify(generalLedger).reconcilePrisoner(prisonNumber)

    verify(ledgerQueryService).listPrisonerBalancesByEstablishment(prisonNumber)
    assertThat(res).isEqualTo(PrisonerEstablishmentBalanceDetailsList(expectedResult))
  }
}
