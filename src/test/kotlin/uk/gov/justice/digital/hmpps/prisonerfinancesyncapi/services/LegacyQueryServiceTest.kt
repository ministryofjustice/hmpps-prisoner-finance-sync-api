package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountCodeLookupRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.PrisonRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionEntryRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.TransactionRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerBalanceService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.TransactionDetailsMapper
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class LegacyQueryServiceTest {

  @Mock private lateinit var prisonRepository: PrisonRepository

  @Mock private lateinit var accountRepository: AccountRepository

  @Mock private lateinit var transactionRepository: TransactionRepository

  @Mock private lateinit var transactionEntryRepository: TransactionEntryRepository

  @Mock private lateinit var accountCodeLookupRepository: AccountCodeLookupRepository

  @Mock private lateinit var transactionDetailsMapper: TransactionDetailsMapper

  @Mock private lateinit var ledgerBalanceService: LedgerBalanceService

  @Mock private lateinit var timeConversionService: TimeConversionService

  @Spy
  @InjectMocks private lateinit var ledgerQueryService: LedgerQueryService

  private val prisonNumber = "A1234AA"

  @ParameterizedTest
  @CsvSource("2101", "2102", "2103")
  fun `should aggregate total balance for prisoner across establishments`(accountCode: Int) {
    whenever(ledgerQueryService.listPrisonerBalancesByEstablishment(prisonNumber)).thenReturn(
      listOf(
        PrisonerEstablishmentBalanceDetails(
          prisonId = "MDI",
          accountCode = accountCode,
          totalBalance = BigDecimal.valueOf(25),
          holdBalance = BigDecimal.valueOf(0),
        ),
        PrisonerEstablishmentBalanceDetails(
          prisonId = "LEI",
          accountCode = accountCode,
          totalBalance = BigDecimal.valueOf(259.05),
          holdBalance = BigDecimal.valueOf(0),
        ),
      ),
    )

    val balanceByAccountCode = ledgerQueryService.aggregatedLegacyBalanceByPrisoner(prisonNumber)

    verify(ledgerQueryService)
      .listPrisonerBalancesByEstablishment(prisonNumber)

    assertThat(balanceByAccountCode)
      .isEqualByComparingTo("284.05")
  }

  @ParameterizedTest
  @CsvSource("2101", "2102", "2103")
  fun `should return zero balance when all establishment balances are zero`(accountCode: Int) {
    whenever(ledgerQueryService.listPrisonerBalancesByEstablishment(prisonNumber)).thenReturn(
      listOf(
        PrisonerEstablishmentBalanceDetails(
          prisonId = "MDI",
          accountCode = accountCode,
          totalBalance = BigDecimal.valueOf(0),
          holdBalance = BigDecimal.valueOf(0),
        ),
        PrisonerEstablishmentBalanceDetails(
          prisonId = "LEI",
          accountCode = accountCode,
          totalBalance = BigDecimal.valueOf(0),
          holdBalance = BigDecimal.valueOf(0),
        ),
      ),
    )

    val balanceByAccountCode = ledgerQueryService.aggregatedLegacyBalanceByPrisoner(prisonNumber)

    verify(ledgerQueryService)
      .listPrisonerBalancesByEstablishment(prisonNumber)

    assertThat(balanceByAccountCode)
      .isEqualByComparingTo("0")
  }
}
