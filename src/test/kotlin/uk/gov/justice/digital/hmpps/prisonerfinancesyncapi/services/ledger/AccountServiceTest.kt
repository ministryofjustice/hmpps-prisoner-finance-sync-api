package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountCodeLookup
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountCodeLookupRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AccountRepository
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AccountServiceTest {

  @Mock
  private lateinit var accountRepository: AccountRepository

  @Mock
  private lateinit var accountCodeLookupRepository: AccountCodeLookupRepository

  @InjectMocks
  private lateinit var accountService: AccountService

  private val accountCodeForPrison = 1001

  private val prisonId = 1L
  private val prisonNumber = "LEI"

  @Nested
  @DisplayName("resolveAccount")
  inner class ResolveAccount {

    fun makeAccountCodeLookup(accountCode: Int, name: String) = AccountCodeLookup(
      accountCode = 1001,
      name = "Cash",
      classification = "Asset",
      postingType = PostingType.DR,
      parentAccountCode = null,
    )

    fun makeAccount(accountType: AccountType) = Account(
      id = 9876,
      uuid = UUID.randomUUID(),
      prisonId = 42L,
      name = "Test Account",
      accountType = accountType,
      accountCode = 1001,
      prisonNumber = "A1234BC",
      subAccountType = null,
      postingType = PostingType.DR,
    )

    @Test
    fun `should throw IllegalArgumentException when account code is not found at lookup`() {
      val unknownLookupCode = 1234556
      whenever(accountCodeLookupRepository.findById(unknownLookupCode)).thenReturn(Optional.empty())

      assertThrows<IllegalArgumentException> {
        accountService.resolveAccount(unknownLookupCode, prisonNumber, prisonId)
      }
    }

    @Test
    fun `should find a prison account when the code is not a prisoner account`() {
      val testAccount = makeAccount(AccountType.GENERAL_LEDGER)
      whenever(accountCodeLookupRepository.findById(accountCodeForPrison))
        .thenReturn(Optional.of(makeAccountCodeLookup(accountCodeForPrison, "TestName")))

      whenever(accountRepository.findByPrisonIdAndAccountCodeAndPrisonNumberIsNull(prisonId, accountCodeForPrison))
        .thenReturn(testAccount)

      val result = accountService.resolveAccount(accountCodeForPrison, prisonNumber, prisonId)

      assertEquals(testAccount, result)
    }

    @Test
    fun `should create a prison account when the code is not a prisoner account and doesn't find an existing one`() {
      val lookupAccount = makeAccountCodeLookup(accountCodeForPrison, "TestName")
      whenever(accountCodeLookupRepository.findById(accountCodeForPrison))
        .thenReturn(Optional.of(lookupAccount))

      whenever(accountRepository.findByPrisonIdAndAccountCodeAndPrisonNumberIsNull(prisonId, accountCodeForPrison))
        .thenReturn(null)

      whenever(accountRepository.save(any()))
        .thenAnswer { it.arguments[0] as Account }

      val result = accountService.resolveAccount(accountCodeForPrison, prisonNumber, prisonId)

      assertEquals(prisonId, result.prisonId)
      assertEquals(lookupAccount.name, result.name)
      assertEquals(accountCodeForPrison, result.accountCode)
      assertEquals(AccountType.GENERAL_LEDGER, result.accountType)
      assertEquals(lookupAccount.postingType, result.postingType)
    }

    @ParameterizedTest
    @CsvSource("2101", "2102", "2103")
    fun `should find a prisoner account when the code is a prisoner sub account`(accountCode: Int) {
      val testAccount = makeAccount(AccountType.PRISONER)
      whenever(accountCodeLookupRepository.findById(accountCode))
        .thenReturn(Optional.of(makeAccountCodeLookup(accountCode, "TestName")))

      val subAccountType = accountService.getSubAccountTypeFromCode(accountCode) ?: throw AssertionError("Sub account type $accountCode not found")
      whenever(accountRepository.findByPrisonNumberAndSubAccountType(prisonNumber, subAccountType))
        .thenReturn(testAccount)

      val result = accountService.resolveAccount(accountCode, prisonNumber, prisonId)

      assertEquals(testAccount, result)
    }

    @ParameterizedTest
    @CsvSource("2101", "2102", "2103")
    fun `should create a prisoner sub account when doesn't find an existing one`(accountCode: Int) {
      val lookupAccount = makeAccountCodeLookup(accountCode, "TestName")
      whenever(accountCodeLookupRepository.findById(accountCode))
        .thenReturn(Optional.of(lookupAccount))

      val subAccountType = accountService.getSubAccountTypeFromCode(accountCode) ?: throw AssertionError("Sub account type $accountCode not found")
      whenever(accountRepository.findByPrisonNumberAndSubAccountType(prisonNumber, subAccountType))
        .thenReturn(null)

      whenever(accountRepository.save(any()))
        .thenAnswer { it.arguments[0] as Account }

      val result = accountService.resolveAccount(accountCode, prisonNumber, prisonId)

      assertEquals(null, result.prisonId)
      assertEquals("$prisonNumber - $subAccountType", result.name)
      assertEquals(accountCode, result.accountCode)
      assertEquals(prisonNumber, result.prisonNumber)
      assertEquals(subAccountType, result.subAccountType)
      assertEquals(AccountType.PRISONER, result.accountType)
      assertEquals(lookupAccount.postingType, result.postingType)
    }
  }

  @Nested
  @DisplayName("findGeneralLedgerAccount")
  inner class FindGeneralLedgerAccount {
    @Test
    fun `method findGeneralLedgerAccount should call repository`() {
      accountService.findGeneralLedgerAccount(prisonId, accountCodeForPrison)

      verify(accountRepository)
        .findByPrisonIdAndAccountCodeAndPrisonNumberIsNull(prisonId, accountCodeForPrison)
    }
  }

  @Nested
  @DisplayName("createAccount")
  inner class CreateAccount {
    @Test
    fun `method createAccount should throw IllegalArgumentException when account of type Prisoner but prisonerNumber is null`() {
      assertThrows<IllegalArgumentException> {
        accountService.createAccount(prisonId, prisonNumber, AccountType.PRISONER, 2101, postingType = PostingType.DR)
      }
    }
  }

  @Nested
  @DisplayName("createGeneralLedgerAccount")
  inner class CreateGeneralLedgerAccount {
    @Test
    fun `should throw IllegalArgumentException when account code is not found at lookup`() {
      val unknownLookupCode = 1234556
      whenever(accountCodeLookupRepository.findById(unknownLookupCode)).thenReturn(Optional.empty())

      assertThrows<IllegalArgumentException> {
        accountService.createGeneralLedgerAccount(prisonId, unknownLookupCode)
      }
    }
  }
}
