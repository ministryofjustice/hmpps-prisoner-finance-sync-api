package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration.DualMigrationService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration.MigrationService
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DualMigrationServiceTest {

  @Mock
  private lateinit var internalMigrationService: MigrationService

  @Mock
  private lateinit var generalLedgerMigrationService: MigrationService

  @Mock
  private lateinit var generalLedgerForwarder: GeneralLedgerForwarder

  private lateinit var dualMigrationService: DualMigrationService

  private val prisonNumber = "A1234AA"

  @BeforeEach
  fun setup() {
    dualMigrationService = DualMigrationService(internalMigrationService, generalLedgerMigrationService, generalLedgerForwarder)
  }

  @Test
  fun `should return result from internalLedger and call General Ledger when Migrating Prisoner Balances`() {
    val lambdaCaptor = argumentCaptor<() -> UUID>()

    val req = mock<PrisonerBalancesSyncRequest>()
    doNothing().whenever(internalMigrationService).migratePrisonerBalances(prisonNumber, req)

    val res = dualMigrationService.migratePrisonerBalances(prisonNumber, req)

    verify(generalLedgerForwarder).executeIfEnabled(
      eq("Failed to migrate prisoner balances for prisoner $prisonNumber to General Ledger"),
      eq(prisonNumber),
      lambdaCaptor.capture(),
    )

    verifyNoInteractions(generalLedgerMigrationService)

    lambdaCaptor.firstValue.invoke() // Switch service is a mock. Lambda requires a manual trigger to check what's called
    verify(generalLedgerMigrationService).migratePrisonerBalances(prisonNumber, req)

    verify(internalMigrationService).migratePrisonerBalances(prisonNumber, req)
    assertThat(res).isEqualTo(Unit)
  }

  val prisonId = "TES"

  @Test
  fun `should return result from internalLedger and call General Ledger when Migrating General Ledger Balances`() {
    val lambdaCaptor = argumentCaptor<() -> UUID>()

    val req = mock<GeneralLedgerBalancesSyncRequest>()
    doNothing().whenever(internalMigrationService).migrateGeneralLedgerBalances(prisonId, req)

    val res = dualMigrationService.migrateGeneralLedgerBalances(prisonId, req)

    verify(generalLedgerForwarder).executeIfEnabled(
      eq("Failed to migrate general ledger balances for prisoner $prisonId to General Ledger"),
      eq(prisonId),
      lambdaCaptor.capture(),
    )

    verifyNoInteractions(generalLedgerMigrationService)

    lambdaCaptor.firstValue.invoke() // Switch service is a mock. Lambda requires a manual trigger to check what's called
    verify(generalLedgerMigrationService).migrateGeneralLedgerBalances(prisonId, req)

    verify(internalMigrationService).migrateGeneralLedgerBalances(prisonId, req)
    assertThat(res).isEqualTo(Unit)
  }
}
