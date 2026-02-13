package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.migration

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateStatementBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.LedgerAccountMappingService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@DisplayName("General Ledger Migration Service Test")
class GeneralLedgerMigrationServiceTest {

  @Mock
  private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @Spy
  private lateinit var accountMapping: LedgerAccountMappingService

  @Spy
  private val timeConversionService = TimeConversionService()

  @InjectMocks
  private lateinit var generalLedgerMigrationService: GeneralLedgerMigrationService

  fun createSubAccountResponse(subAccountRef: String, parentUUID: UUID) = SubAccountResponse(
    UUID.randomUUID(),
    subAccountRef,
    parentUUID,
    "Test",
    Instant.now(),
  )

  fun createParentAccountResponse(prisonerRef: String, accountId: UUID = UUID.randomUUID(), subAccounts: List<SubAccountResponse> = emptyList()) = AccountResponse(
    accountId,
    prisonerRef,
    "Test",
    Instant.now(),
    subAccounts,
  )

  val prisonNumber = "A1234AA"

  @Test
  fun `should aggregate multiple balances of different account codes from different establishments when called`() {
    val req = PrisonerBalancesSyncRequest(
      listOf(
        PrisonerAccountPointInTimeBalance(
          "TES",
          2101,
          BigDecimal("10"),
          BigDecimal.ZERO,
          1,
          LocalDateTime.now() - Duration.ofDays(1),
        ),
        PrisonerAccountPointInTimeBalance(
          "MDI",
          2101,
          BigDecimal("20"),
          BigDecimal.ZERO,
          1,
          LocalDateTime.now(),
        ),
        PrisonerAccountPointInTimeBalance(
          "MDI",
          2102,
          BigDecimal("33"),
          BigDecimal.ZERO,
          1,
          LocalDateTime.now() - Duration.ofDays(3),
        ),
      ),
    )

    val parentAccountid = UUID.randomUUID()

    val subAccounts = mutableMapOf<Int, SubAccountResponse>()

    for (balance in req.accountBalances) {
      subAccounts[balance.accountCode] = createSubAccountResponse(
        accountMapping.mapPrisonerSubAccount(balance.accountCode),
        parentAccountid,
      )
    }

    val parentAccount = createParentAccountResponse(prisonNumber, parentAccountid, subAccounts.values.toList())

    whenever(generalLedgerApiClient.findAccountByReference(prisonNumber)).thenReturn(parentAccount)

    generalLedgerMigrationService.migratePrisonerBalances(prisonNumber, req)

    for (subAccount in subAccounts.values) {
      val request = CreateStatementBalanceRequest(
        req.accountBalances
          .filter { accountMapping.mapPrisonerSubAccount(it.accountCode) == subAccount.reference }
          .sumOf { it.balance }.toPence(),
        req.accountBalances
          .filter { accountMapping.mapPrisonerSubAccount(it.accountCode) == subAccount.reference }
          .maxOf { timeConversionService.toUtcInstant(it.asOfTimestamp) },
      )
      verify(generalLedgerApiClient).migrateSubAccountBalance(subAccount.id, request)
    }
  }

  @Test
  fun `should create Parent Account and Sub accounts when they are not found and migrate balances`() {
    val req = PrisonerBalancesSyncRequest(
      listOf(
        PrisonerAccountPointInTimeBalance(
          "TES",
          2101,
          BigDecimal("10"),
          BigDecimal.ZERO,
          1,
          LocalDateTime.now() - Duration.ofDays(1),
        ),
        PrisonerAccountPointInTimeBalance(
          "MDI",
          2101,
          BigDecimal("0"),
          BigDecimal.ZERO,
          1,
          LocalDateTime.now(),
        ),
        PrisonerAccountPointInTimeBalance(
          "KMI",
          2101,
          BigDecimal("20"),
          BigDecimal.ZERO,
          1,
          LocalDateTime.now() - Duration.ofDays(5),
        ),
        PrisonerAccountPointInTimeBalance(
          "MDI",
          2102,
          BigDecimal("33"),
          BigDecimal.ZERO,
          1,
          LocalDateTime.now() - Duration.ofDays(3),
        ),
      ),
    )

    val parentAccountid = UUID.randomUUID()

    val subAccounts = mutableMapOf<Int, SubAccountResponse>()

    for (balance in req.accountBalances) {
      subAccounts[balance.accountCode] = createSubAccountResponse(
        accountMapping.mapPrisonerSubAccount(balance.accountCode),
        parentAccountid,
      )
    }

    val parentAccount = createParentAccountResponse(prisonNumber, parentAccountid)

    whenever(generalLedgerApiClient.findAccountByReference(prisonNumber)).thenReturn(null)
    whenever(generalLedgerApiClient.createAccount(prisonNumber)).thenReturn(parentAccount)

    for (subAccount in subAccounts.values) {
      whenever(generalLedgerApiClient.createSubAccount(parentAccount.id, subAccount.reference)).thenReturn(subAccount)
    }

    generalLedgerMigrationService.migratePrisonerBalances(prisonNumber, req)

    verify(generalLedgerApiClient).createAccount(prisonNumber)

    for (subAccount in subAccounts.values) {
      verify(generalLedgerApiClient).createSubAccount(parentAccount.id, subAccount.reference)
    }

    for (subAccount in subAccounts.values) {
      val request = CreateStatementBalanceRequest(
        req.accountBalances
          .filter { accountMapping.mapPrisonerSubAccount(it.accountCode) == subAccount.reference }
          .sumOf { it.balance }.toPence(),
        req.accountBalances
          .filter { accountMapping.mapPrisonerSubAccount(it.accountCode) == subAccount.reference }
          .maxOf { timeConversionService.toUtcInstant(it.asOfTimestamp) },
      )
      verify(generalLedgerApiClient).migrateSubAccountBalance(subAccount.id, request)
    }
  }
}
