package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.GeneralLedgerTransactionMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

@ExtendWith(MockitoExtension::class)
class GeneralLedgerServiceTest {

  @Mock
  private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @Mock
  private lateinit var telemetryClient: TelemetryClient

  @Mock
  private lateinit var ledgerQueryService: LedgerQueryService

  @Mock
  private lateinit var generalLedgerTransactionMappingRepository: GeneralLedgerTransactionMappingRepository

  @Spy
  private lateinit var idempotencyService: GeneralLedgerIdempotencyService

  @Spy
  private lateinit var accountMapping: LedgerAccountMappingService

  @Spy
  private val timeConversionService = TimeConversionService()

  @Mock
  private lateinit var generalLedgerAccountResolver: GeneralLedgerAccountResolver

  @InjectMocks
  private lateinit var generalLedgerService: GeneralLedgerService

  private lateinit var listAppender: ListAppender<ILoggingEvent>

  private val logger = LoggerFactory.getLogger(GeneralLedgerService::class.java) as Logger

  private val offenderDisplayId = "A1234AA"

  fun mockAccount(reference: String, accountUUID: UUID = UUID.randomUUID(), subAccounts: List<SubAccountResponse> = emptyList()): AccountResponse {
    val accountResponse = AccountResponse(
      id = accountUUID,
      reference = reference,
      createdBy = "OMS_OWNER",
      createdAt = Instant.now(),
      subAccounts = subAccounts,
    )
    whenever(generalLedgerApiClient.findAccountByReference(reference))
      .thenReturn(accountResponse)

    return accountResponse
  }

  private fun makeMockSubAccountResolver(request: SyncOffenderTransactionRequest): MutableMap<String, UUID> {
    val mapUUID = mutableMapOf<String, UUID>()

    for ((i, transaction) in request.offenderTransactions.withIndex()) {
      for (entry in transaction.generalLedgerEntries) {
        mapUUID["$i-${entry.entrySequence}"] = UUID.randomUUID()
        whenever(
          generalLedgerAccountResolver.resolveSubAccount(
            eq(request.caseloadId),
            eq(transaction.offenderDisplayId),
            eq(entry.code),
            eq(transaction.type),
            any(),
          ),
        )
          .thenReturn(mapUUID["$i-${entry.entrySequence}"])
      }
    }
    return mapUUID
  }

  fun mockPostTransaction(request: SyncOffenderTransactionRequest, postingsGL: List<CreatePostingRequest>, returnUUID: UUID = UUID.randomUUID()): CreateTransactionRequest {
    val requestGL = CreateTransactionRequest(
      reference = request.offenderTransactions[0].reference!!,
      description = request.offenderTransactions[0].description,
      timestamp = timeConversionService.toUtcInstant(request.transactionTimestamp),
      amount = request.offenderTransactions[0].amount.toPence(),
      postings = postingsGL,
    )

    whenever(generalLedgerApiClient.postTransaction(eq(requestGL), any())).thenReturn(returnUUID)

    return requestGL
  }

  @BeforeEach
  fun setup() {
    listAppender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(listAppender)

    accountMapping = LedgerAccountMappingService()
  }

  @AfterEach
  fun tearDown() {
    logger.detachAppender(listAppender)
  }

  @Nested
  @DisplayName("reconcilePrisonerBalances")
  inner class ReconcilePrisonerBalances {

    val prisonNumber = "A1234AA"
    val prisonerAccounts = listOf("CASH", "SAVINGS", "SPENDS")

    @Test
    fun `Should log reconciliation error when GL does NOT match internal Ledger`() {
      val parentUUID = UUID.randomUUID()
      val subAccounts = mutableListOf<SubAccountResponse>()

      for (account in prisonerAccounts) {
        subAccounts.add(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = account,
            parentAccountId = parentUUID,
            createdBy = "TEST",
            createdAt = Instant.now(),
          ),
        )
      }

      mockAccount(offenderDisplayId, parentUUID, subAccounts)

      // GL accounts
      val testGlBalance = SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 5)
      for (account in subAccounts) {
        whenever(generalLedgerApiClient.findSubAccountBalanceByAccountId(account.id))
          .thenReturn(testGlBalance)
      }

      // InternalLedger
      val internalLedgerBalances = listOf(
        PrisonerEstablishmentBalanceDetails("LEI", 2101, BigDecimal("4"), BigDecimal.ZERO),
        PrisonerEstablishmentBalanceDetails("MDI", 2101, BigDecimal("4"), BigDecimal.ZERO),
        PrisonerEstablishmentBalanceDetails("LEI", 2102, BigDecimal("7"), BigDecimal.ZERO),
        PrisonerEstablishmentBalanceDetails("LEI", 2103, BigDecimal("2"), BigDecimal.ZERO),
        PrisonerEstablishmentBalanceDetails("MDI", 2103, BigDecimal("4"), BigDecimal.ZERO),
      )
      whenever(ledgerQueryService.listPrisonerBalancesByEstablishment(prisonNumber)).thenReturn(internalLedgerBalances)

      whenever(ledgerQueryService.aggregatedLegacyBalanceForAccountCode(2101, internalLedgerBalances)).thenReturn(8)
      whenever(ledgerQueryService.aggregatedLegacyBalanceForAccountCode(2102, internalLedgerBalances)).thenReturn(7)
      whenever(ledgerQueryService.aggregatedLegacyBalanceForAccountCode(2103, internalLedgerBalances)).thenReturn(6)

      generalLedgerService.reconcilePrisoner(prisonNumber)

      val logs = listAppender.list.map { it.formattedMessage }
      assertThat(logs).filteredOn { it.contains("Discrepancy found for prisoner") }.hasSize(3)

      for (accountCode in accountMapping.prisonerSubAccounts.values) {
        verify(ledgerQueryService).aggregatedLegacyBalanceForAccountCode(accountCode, internalLedgerBalances)
      }

      verify(generalLedgerApiClient).findAccountByReference(prisonNumber)

      for (account in subAccounts) {
        verify(generalLedgerApiClient).findSubAccountBalanceByAccountId(account.id)
      }
    }

    @Test
    fun `Should not log reconciliation error when GL matches internal Ledger`() {
      val parentUUID = UUID.randomUUID()
      val subAccounts = mutableListOf<SubAccountResponse>()

      for (account in prisonerAccounts) {
        subAccounts.add(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = account,
            parentAccountId = parentUUID,
            createdBy = "TEST",
            createdAt = Instant.now(),
          ),
        )
      }

      mockAccount(offenderDisplayId, parentUUID, subAccounts)

      // GL accounts
      val testGlBalance = SubAccountBalanceResponse(UUID.randomUUID(), Instant.now(), 5)
      for (account in subAccounts) {
        whenever(generalLedgerApiClient.findSubAccountBalanceByAccountId(account.id))
          .thenReturn(testGlBalance)
      }

      // InternalLedger
      val internalLedgerBalances = listOf(
        PrisonerEstablishmentBalanceDetails("LEI", 2101, BigDecimal("4"), BigDecimal.ZERO),
        PrisonerEstablishmentBalanceDetails("MDI", 2101, BigDecimal("1"), BigDecimal.ZERO),
        PrisonerEstablishmentBalanceDetails("LEI", 2102, BigDecimal("5"), BigDecimal.ZERO),
        PrisonerEstablishmentBalanceDetails("LEI", 2103, BigDecimal("1"), BigDecimal.ZERO),
        PrisonerEstablishmentBalanceDetails("MDI", 2103, BigDecimal("4"), BigDecimal.ZERO),
      )
      whenever(ledgerQueryService.listPrisonerBalancesByEstablishment(prisonNumber)).thenReturn(internalLedgerBalances)

      whenever(ledgerQueryService.aggregatedLegacyBalanceForAccountCode(2101, internalLedgerBalances)).thenReturn(5)
      whenever(ledgerQueryService.aggregatedLegacyBalanceForAccountCode(2102, internalLedgerBalances)).thenReturn(5)
      whenever(ledgerQueryService.aggregatedLegacyBalanceForAccountCode(2103, internalLedgerBalances)).thenReturn(5)

      generalLedgerService.reconcilePrisoner(prisonNumber)

      val logs = listAppender.list.map { it.formattedMessage }

      assertThat(logs).isEmpty()

      for (accountCode in accountMapping.prisonerSubAccounts.values) {
        verify(ledgerQueryService).aggregatedLegacyBalanceForAccountCode(accountCode, internalLedgerBalances)
      }

      verify(generalLedgerApiClient).findAccountByReference(prisonNumber)

      for (account in subAccounts) {
        verify(generalLedgerApiClient).findSubAccountBalanceByAccountId(account.id)
      }
    }

    @Test
    fun `should calculate legacy balances when called`() {
      val mockList = mock<List<PrisonerEstablishmentBalanceDetails>>()
      whenever(ledgerQueryService.listPrisonerBalancesByEstablishment(prisonNumber)).thenReturn(mockList)
      generalLedgerService.reconcilePrisoner(prisonNumber)

      verify(ledgerQueryService).listPrisonerBalancesByEstablishment(prisonNumber)

      for (accountCode in accountMapping.prisonerSubAccounts.values) {
        verify(ledgerQueryService).aggregatedLegacyBalanceForAccountCode(accountCode, mockList)
      }
    }

    @Test
    fun `Should log error when prisoner does not have any sub account`() {
      val mockAccount = mockAccount(offenderDisplayId, subAccounts = emptyList())

      whenever(generalLedgerApiClient.findAccountByReference(prisonNumber)).thenReturn(mockAccount)

      generalLedgerService.reconcilePrisoner(prisonNumber)

      verify(generalLedgerApiClient).findAccountByReference(prisonNumber)
      verifyNoMoreInteractions(generalLedgerApiClient)

      val logs = listAppender.list.map { it.formattedMessage }

      assertThat(logs).contains("No sub accounts found for prisoner $prisonNumber")
    }

    @Test
    fun `Should log error when prisoner parent account is not found`() {
      whenever(generalLedgerApiClient.findAccountByReference(prisonNumber)).thenReturn(null)

      generalLedgerService.reconcilePrisoner(prisonNumber)

      verify(generalLedgerApiClient).findAccountByReference(prisonNumber)
      verifyNoMoreInteractions(generalLedgerApiClient)

      val logs = listAppender.list.map { it.formattedMessage }

      assertThat(logs).contains("No parent account found for prisoner $prisonNumber")
    }

    @Test
    fun `Should log error when prisoner sub balance account is not found`() {
      val parentUUID = UUID.randomUUID()
      val subAccounts = mutableListOf<SubAccountResponse>()

      for (account in prisonerAccounts) {
        val subAccount =
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = account,
            parentAccountId = parentUUID,
            createdBy = "TEST",
            createdAt = Instant.now(),
          )

        subAccounts.add(subAccount)
        whenever(generalLedgerApiClient.findSubAccountBalanceByAccountId(subAccount.id))
          .thenReturn(null)
      }

      mockAccount(offenderDisplayId, parentUUID, subAccounts)

      generalLedgerService.reconcilePrisoner(prisonNumber)

      verify(generalLedgerApiClient).findAccountByReference(prisonNumber)

      val logs = listAppender.list.map { it.formattedMessage }

      for (account in subAccounts) {
        assertThat(logs).contains("No balance found for account ${account.id} but it was in the parent subaccounts list")
        verify(generalLedgerApiClient).findSubAccountBalanceByAccountId(account.id)
      }
    }

    @Test
    fun `should get all Prisoner SUB accounts`() {
      val parentUUID = UUID.randomUUID()
      val subAccounts = mutableListOf<SubAccountResponse>()

      for (account in prisonerAccounts) {
        subAccounts.add(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = account,
            parentAccountId = parentUUID,
            createdBy = "TEST",
            createdAt = Instant.now(),
          ),
        )
      }

      mockAccount(offenderDisplayId, parentUUID, subAccounts)

      for (account in subAccounts) {
        whenever(generalLedgerApiClient.findSubAccountBalanceByAccountId(account.id)).thenReturn(mock())
      }

      generalLedgerService.reconcilePrisoner(prisonNumber)

      verify(generalLedgerApiClient).findAccountByReference(prisonNumber)

      for (account in subAccounts) {
        verify(generalLedgerApiClient).findSubAccountBalanceByAccountId(account.id)
      }
    }
  }

  @Nested
  @DisplayName("syncOffenderTransaction")
  inner class SyncOffenderTransaction {
    @Test
    fun `should propagate exception from resolveSubAccount`() {
      val glEntry = GeneralLedgerEntry(
        entrySequence = 1,
        code = 1,
        postingType = "CREDIT",
        amount = BigDecimal("100.00"),
      )

      val offenderTransaction = OffenderTransaction(
        offenderDisplayId = "A1234BC",
        type = "CREDIT",
        generalLedgerEntries = listOf(glEntry),
        reference = "TX123",
        description = "Test Transaction",
        entrySequence = 1,
        offenderId = 1234567,
        offenderBookingId = 1,
        subAccountType = "",
        postingType = "CR",
        amount = BigDecimal("100.00"),
      )

      val request = SyncOffenderTransactionRequest(
        caseloadId = "LEI",
        transactionTimestamp = LocalDateTime.now(),
        offenderTransactions = listOf(offenderTransaction),
        transactionId = 1234567,
        requestId = UUID.randomUUID(),
        createdBy = Instant.now().toString(),
        createdAt = LocalDateTime.now(),
        createdByDisplayName = "test-user",
        lastModifiedAt = LocalDateTime.now(),
        lastModifiedBy = "test-user",
        lastModifiedByDisplayName = "Test User",
      )

      val expectedError = RuntimeException("Network Error")

      whenever(
        generalLedgerAccountResolver.resolveSubAccount(
          any<String>(),
          any<String>(),
          any(),
          any<String>(),
          any(),
        ),
      ).thenThrow(expectedError)

      assertThatThrownBy {
        generalLedgerService.syncOffenderTransaction(request)
      }.isSameAs(expectedError)
    }

    @Test
    fun `should return list of UUIDs when multiple transactions are processed`() {
      val tx1 = OffenderTransaction(
        entrySequence = 1,
        offenderId = 1L,
        offenderDisplayId = offenderDisplayId,
        offenderBookingId = 100L,
        subAccountType = "SPND",
        postingType = "DR",
        type = "CANT",
        description = "Tx 1",
        amount = BigDecimal("10.00"),
        reference = "REF1",
        generalLedgerEntries = listOf(GeneralLedgerEntry(1, 2101, "DR", BigDecimal("10.00"))),
      )

      val tx2 = tx1.copy(
        offenderDisplayId = "B1234BB",
        description = "Tx 2",
        reference = "REF2",
        entrySequence = 2,
      )

      val request = mock<SyncOffenderTransactionRequest>()
      whenever(request.transactionId).thenReturn(12345L)
      whenever(request.caseloadId).thenReturn("MDI")
      whenever(request.transactionTimestamp).thenReturn(LocalDateTime.now())
      whenever(request.offenderTransactions).thenReturn(listOf(tx1, tx2))

      whenever(generalLedgerAccountResolver.resolveSubAccount(any(), any(), any(), any(), any()))
        .thenReturn(
          UUID.randomUUID(),
        )

      val uuid1 = UUID.randomUUID()
      val uuid2 = UUID.randomUUID()

      whenever(generalLedgerApiClient.postTransaction(any(), any()))
        .thenReturn(uuid1)
        .thenReturn(uuid2)

      val result = generalLedgerService.syncOffenderTransaction(request)

      assertThat(result).hasSize(2)
      assertThat(result).containsExactlyInAnyOrder(uuid1, uuid2)
      verify(generalLedgerTransactionMappingRepository, times(2)).save(any())
    }

    @Test
    fun `should save mapping with correct NOMIS transaction ID`() {
      val request = SyncOffenderTransactionRequest(
        transactionId = 12345L,
        caseloadId = "TEST",
        transactionTimestamp = LocalDateTime.now(),
        createdAt = LocalDateTime.now().plusSeconds(5),
        createdBy = "OMS_OWNER",
        requestId = UUID.randomUUID(),
        createdByDisplayName = "OMS_OWNER",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = listOf(
          OffenderTransaction(
            entrySequence = 1,
            offenderId = 5306470,
            offenderDisplayId = offenderDisplayId,
            offenderBookingId = 2970777,
            subAccountType = "SPND",
            postingType = "CR",
            type = "CANT",
            description = "Test Transaction for Balance Check",
            amount = BigDecimal("10.00"),
            reference = "REF-54322L",
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, 1501, "CR", BigDecimal("10.00")),
              GeneralLedgerEntry(2, 2101, "DR", BigDecimal("10.00")),
            ),
          ),
        ),
      )

      makeMockSubAccountResolver(request)

      whenever(generalLedgerApiClient.postTransaction(any(), any())).thenReturn(UUID.randomUUID())

      generalLedgerService.syncOffenderTransaction(request)

      verify(generalLedgerTransactionMappingRepository).save(
        org.mockito.kotlin.check {
          assertThat(it.legacyTransactionId).isEqualTo(request.transactionId)
        },
      )
    }
  }

  @Nested
  @DisplayName("syncGeneralLedgerTransaction")
  inner class SyncGeneralLedgerTransaction {

    @Test
    fun `should throw NotImplementedError`() {
      val request = mock<SyncGeneralLedgerTransactionRequest>()

      assertThatThrownBy {
        generalLedgerService.syncGeneralLedgerTransaction(request)
      }.isInstanceOf(NotImplementedError::class.java)
        .hasMessageContaining("not yet supported")
    }

    @Test
    fun `should throw exception when there are no transactions`() {
      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()
      val prisonId = "LEI"

      val requestWithNoOffenderTransactions = SyncOffenderTransactionRequest(
        transactionId = transactionId,
        caseloadId = prisonId,
        transactionTimestamp = timestamp,
        createdAt = timestamp.plusSeconds(5),
        createdBy = "OMS_OWNER",
        requestId = UUID.randomUUID(),
        createdByDisplayName = "OMS_OWNER",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = emptyList(),
      )

      assertThatThrownBy {
        generalLedgerService.syncOffenderTransaction(requestWithNoOffenderTransactions)
      }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `should call POST transaction`() {
      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()
      val prisonId = "LEI"
      val amount = BigDecimal("5.00")

      val prisonerAccountCode = 2102
      val prisonAccountCode = 1502

      val transactionType = "ADV"

      val request = SyncOffenderTransactionRequest(
        transactionId = transactionId,
        caseloadId = prisonId,
        transactionTimestamp = timestamp,
        createdAt = timestamp.plusSeconds(5),
        createdBy = "OMS_OWNER",
        requestId = UUID.randomUUID(),
        createdByDisplayName = "OMS_OWNER",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = listOf(
          OffenderTransaction(
            entrySequence = 1,
            offenderId = 5306470,
            offenderDisplayId = offenderDisplayId,
            offenderBookingId = 2970777,
            subAccountType = "SPND",
            postingType = "CR",
            type = transactionType,
            description = "Test Transaction for Balance Check",
            amount = amount,
            reference = "REF-$transactionId",
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, prisonAccountCode, "CR", amount),
              GeneralLedgerEntry(2, prisonerAccountCode, "DR", amount),
            ),
          ),
        ),
      )

      val mapOfUUIDs = makeMockSubAccountResolver(request)

      val glTransactionRequest = CreateTransactionRequest(
        reference = request.offenderTransactions[0].reference!!,
        description = request.offenderTransactions[0].description,
        timestamp = timeConversionService.toUtcInstant(request.transactionTimestamp),
        amount = amount.toPence(),
        postings = listOf(
          CreatePostingRequest(subAccountId = mapOfUUIDs.getValue("0-1"), type = CreatePostingRequest.Type.CR, amount = amount.toPence()),
          CreatePostingRequest(subAccountId = mapOfUUIDs.getValue("0-2"), type = CreatePostingRequest.Type.DR, amount = amount.toPence()),
        ),
      )
      val expectedUUID = UUID.randomUUID()
      whenever(generalLedgerApiClient.postTransaction(eq(glTransactionRequest), any())).thenReturn(expectedUUID)

      generalLedgerService.syncOffenderTransaction(request)

      verify(generalLedgerApiClient).postTransaction(eq(glTransactionRequest), any())
    }

    @Test
    fun `should post multiple transactions where there are multiple prisoners`() {
      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()
      val prisonId = "LEI"
      val amount = BigDecimal("5.00")

      val prisoner1DisplayId = "A1234AA"
      val prisoner2DisplayId = "B4321ZZ"

      val prisonerAccountCode = 2102
      val prisonAccountCode = 1502

      val transactionType = "CANT"

      val requestTransactionWithMultiplePrisoners = SyncOffenderTransactionRequest(
        transactionId = transactionId,
        caseloadId = prisonId,
        transactionTimestamp = timestamp,
        createdAt = timestamp.plusSeconds(5),
        createdBy = "OMS_OWNER",
        requestId = UUID.randomUUID(),
        createdByDisplayName = "OMS_OWNER",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        offenderTransactions = listOf(
          OffenderTransaction(
            entrySequence = 1,
            offenderId = 5306470,
            offenderDisplayId = prisoner1DisplayId,
            offenderBookingId = 2970777,
            subAccountType = "SPND",
            postingType = "CR",
            type = transactionType,
            description = "Test Transaction for Balance Check",
            amount = amount,
            reference = "REF-$transactionId",
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, prisonAccountCode, "CR", amount),
              GeneralLedgerEntry(2, prisonerAccountCode, "DR", amount),
            ),
          ),
          OffenderTransaction(
            entrySequence = 2,
            offenderId = 5306471,
            offenderDisplayId = prisoner2DisplayId,
            offenderBookingId = 2970777,
            subAccountType = "SPND",
            postingType = "CR",
            type = transactionType,
            description = "Test Transaction for Balance Check",
            amount = amount,
            reference = "REF-$transactionId",
            generalLedgerEntries = listOf(
              GeneralLedgerEntry(1, prisonAccountCode, "CR", amount),
              GeneralLedgerEntry(2, prisonerAccountCode, "DR", amount),
            ),
          ),
        ),
      )

      val mapUUID = makeMockSubAccountResolver(requestTransactionWithMultiplePrisoners)

      val glTransactionRequestPrisoner1 = CreateTransactionRequest(
        reference = requestTransactionWithMultiplePrisoners.offenderTransactions[0].reference!!,
        description = requestTransactionWithMultiplePrisoners.offenderTransactions[0].description,
        timestamp = timeConversionService.toUtcInstant(requestTransactionWithMultiplePrisoners.transactionTimestamp),
        amount = amount.toPence(),
        postings = listOf(
          CreatePostingRequest(subAccountId = mapUUID.getValue("0-1"), type = CreatePostingRequest.Type.CR, amount = amount.toPence()),
          CreatePostingRequest(subAccountId = mapUUID.getValue("0-2"), type = CreatePostingRequest.Type.DR, amount = amount.toPence()),
        ),
      )

      val glTransactionRequestPrisoner2 = CreateTransactionRequest(
        reference = requestTransactionWithMultiplePrisoners.offenderTransactions[1].reference!!,
        description = requestTransactionWithMultiplePrisoners.offenderTransactions[1].description,
        timestamp = timeConversionService.toUtcInstant(requestTransactionWithMultiplePrisoners.transactionTimestamp),
        amount = amount.toPence(),
        postings = listOf(
          CreatePostingRequest(subAccountId = mapUUID.getValue("1-1"), type = CreatePostingRequest.Type.CR, amount = amount.toPence()),
          CreatePostingRequest(subAccountId = mapUUID.getValue("1-2"), type = CreatePostingRequest.Type.DR, amount = amount.toPence()),
        ),
      )
      whenever(generalLedgerApiClient.postTransaction(eq(glTransactionRequestPrisoner1), any())).thenReturn(UUID.randomUUID())
      whenever(generalLedgerApiClient.postTransaction(eq(glTransactionRequestPrisoner2), any())).thenReturn(UUID.randomUUID())

      generalLedgerService.syncOffenderTransaction(requestTransactionWithMultiplePrisoners)

      verify(generalLedgerApiClient).postTransaction(eq(glTransactionRequestPrisoner1), any())
      verify(generalLedgerApiClient).postTransaction(eq(glTransactionRequestPrisoner2), any())
      verify(generalLedgerTransactionMappingRepository, times(2)).save(any())
    }
  }
}
