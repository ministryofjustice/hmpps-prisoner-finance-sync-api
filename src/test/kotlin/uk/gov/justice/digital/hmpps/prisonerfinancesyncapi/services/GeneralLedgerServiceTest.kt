package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
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
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlPostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlSubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.toGLPostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

@ExtendWith(MockitoExtension::class)
class GeneralLedgerServiceTest {

  @Mock
  private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @Mock
  private lateinit var ledgerQueryService: LedgerQueryService

  @InjectMocks
  private lateinit var generalLedgerService: GeneralLedgerService

  @Spy
  private lateinit var timeConversionService: TimeConversionService

  @Spy
  private lateinit var accountMapping: LedgerAccountMappingService

  private lateinit var listAppender: ListAppender<ILoggingEvent>

  private val logger = LoggerFactory.getLogger(GeneralLedgerService::class.java) as Logger

  private val offenderDisplayId = "A1234AA"

  fun mockAccount(reference: String, accountUUID: UUID = UUID.randomUUID()): GlAccountResponse {
    val mockGLAccountResponse = mock<GlAccountResponse>()
    whenever(generalLedgerApiClient.findAccountByReference(reference))
      .thenReturn(mockGLAccountResponse)

    whenever(mockGLAccountResponse.id).thenReturn(accountUUID)

    return mockGLAccountResponse
  }

  fun mockSubAccount(parentReference: String, subAccountReference: String, accountUUID: UUID = UUID.randomUUID()): GlSubAccountResponse {
    val mockGLSubAccountResponse = mock<GlSubAccountResponse>()
    whenever(generalLedgerApiClient.findSubAccount(parentReference, subAccountReference))
      .thenReturn(mockGLSubAccountResponse)

    whenever(mockGLSubAccountResponse.id).thenReturn(accountUUID)

    return mockGLSubAccountResponse
  }

  fun mockAccountNotFoundAndCreateAccount(reference: String, accountUUID: UUID = UUID.randomUUID()) {
    whenever(generalLedgerApiClient.findAccountByReference(reference)).thenReturn(null)

    val mockGlAccountResponse = mock<GlAccountResponse>()
    whenever(generalLedgerApiClient.createAccount(reference))
      .thenReturn(mockGlAccountResponse)
    whenever(mockGlAccountResponse.id).thenReturn(accountUUID)
  }

  fun mockSubAccountNotFoundAndCreateSubAccount(parentReference: String, parentReferenceUUId: UUID, subAccountReference: String) {
    whenever(
      generalLedgerApiClient.findSubAccount(
        parentReference,
        subAccountReference,
      ),
    ).thenReturn(null)

    val mockPrisonSubAccountResponse = mock<GlSubAccountResponse>()
    whenever(mockPrisonSubAccountResponse.id).thenReturn(UUID.randomUUID())
    whenever(
      generalLedgerApiClient.createSubAccount(
        parentReferenceUUId,
        subAccountReference,
      ),
    ).thenReturn(mockPrisonSubAccountResponse)
  }

  fun mockPostTransaction(request: SyncOffenderTransactionRequest, postingsGL: List<GlPostingRequest>, returnUUID: UUID = UUID.randomUUID()): GlTransactionRequest {
    val requestGL = GlTransactionRequest(
      request.offenderTransactions[0].reference!!,
      request.offenderTransactions[0].description,
      timeConversionService.toUtcInstant(request.transactionTimestamp),
      request.offenderTransactions[0].amount.toPence(),
      postingsGL,
    )

    whenever(generalLedgerApiClient.postTransaction(eq(requestGL), any())).thenReturn(returnUUID)

    return requestGL
  }

  @BeforeEach
  fun setup() {
    listAppender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(listAppender)
  }

  @AfterEach
  fun tearDown() {
    logger.detachAppender(listAppender)
  }

  @Nested
  @DisplayName("syncOffenderTransaction")
  inner class SyncOffenderTransaction {

    @Test
    fun `should log accountId when existing prison and prisoner account found`() {
      val request = createRequest(offenderDisplayId)
      val accountUuidPrison = UUID.randomUUID()
      val accountUuidPrisoner = UUID.randomUUID()

      mockAccount(request.caseloadId, accountUuidPrison)
      mockAccount(offenderDisplayId, accountUuidPrisoner)

      val subAccountPrisonUUID = UUID.randomUUID()
      val subAccountPrisonerUUID = UUID.randomUUID()
      mockSubAccount(
        request.caseloadId,
        accountMapping.mapPrisonSubAccount(
          request.offenderTransactions[0].generalLedgerEntries[0].code,
          request.offenderTransactions[0].type,
        ),
        subAccountPrisonUUID,
      )
      mockSubAccount(
        offenderDisplayId,
        accountMapping.mapPrisonerSubAccount(
          request.offenderTransactions[0].generalLedgerEntries[1].code,
        ),
        subAccountPrisonerUUID,
      )

      val transactionRequestUUID = UUID.randomUUID()
      mockPostTransaction(
        request,
        listOf<GlPostingRequest>(
          GlPostingRequest(
            subAccountPrisonUUID,
            request.offenderTransactions[0].generalLedgerEntries[0].postingType.toGLPostingType(),
            request.offenderTransactions[0].generalLedgerEntries[0].amount.toPence(),
          ),
          GlPostingRequest(
            subAccountPrisonerUUID,
            request.offenderTransactions[0].generalLedgerEntries[1].postingType.toGLPostingType(),
            request.offenderTransactions[0].generalLedgerEntries[1].amount.toPence(),
          ),
        ),
        transactionRequestUUID,
      )

      val result = generalLedgerService.syncOffenderTransaction(request)

      assertThat(result).isEqualTo(transactionRequestUUID)

      verify(generalLedgerApiClient).findAccountByReference(offenderDisplayId)
      assertThat(result).isNotNull()

      val logs = listAppender.list.map { it.formattedMessage }
      assertThat(logs).contains("General Ledger account found for '${request.caseloadId}' (UUID: $accountUuidPrison)")
      assertThat(logs).contains("General Ledger account found for '$offenderDisplayId' (UUID: $accountUuidPrisoner)")
    }

    @Test
    fun `should create parent account for prison when account not found`() {
      val request = createRequest(offenderDisplayId)

      mockAccount(offenderDisplayId)

      whenever(generalLedgerApiClient.findAccountByReference(request.caseloadId)).thenReturn(null)

      whenever(generalLedgerApiClient.createAccount(request.caseloadId))
        .thenReturn(mock<GlAccountResponse>())

      val subAccountPrisonUUID = UUID.randomUUID()
      val subAccountPrisonerUUID = UUID.randomUUID()
      mockSubAccount(
        request.caseloadId,
        accountMapping.mapPrisonSubAccount(
          request.offenderTransactions[0].generalLedgerEntries[0].code,
          request.offenderTransactions[0].type,
        ),
        subAccountPrisonUUID,
      )

      mockSubAccount(
        offenderDisplayId,
        accountMapping.mapPrisonerSubAccount(
          request.offenderTransactions[0].generalLedgerEntries[1].code,
        ),
        subAccountPrisonerUUID,
      )

      val transactionRequestUUID = UUID.randomUUID()
      mockPostTransaction(
        request,
        listOf<GlPostingRequest>(
          GlPostingRequest(
            subAccountPrisonUUID,
            request.offenderTransactions[0].generalLedgerEntries[0].postingType.toGLPostingType(),
            request.offenderTransactions[0].generalLedgerEntries[0].amount.toPence(),
          ),
          GlPostingRequest(
            subAccountPrisonerUUID,
            request.offenderTransactions[0].generalLedgerEntries[1].postingType.toGLPostingType(),
            request.offenderTransactions[0].generalLedgerEntries[1].amount.toPence(),
          ),
        ),
        transactionRequestUUID,
      )

      val result = generalLedgerService.syncOffenderTransaction(request)
      assertThat(result).isEqualTo(transactionRequestUUID)

      verify(generalLedgerApiClient, times(1)).findAccountByReference(request.caseloadId)
      assertThat(result).isNotNull()

      verify(generalLedgerApiClient, times(1)).createAccount(request.caseloadId)
    }

    @Test
    fun `should create parent account for prisoner when account not found`() {
      val request = createRequest(offenderDisplayId)

      mockAccountNotFoundAndCreateAccount(offenderDisplayId)
      mockAccountNotFoundAndCreateAccount(request.caseloadId)

      val subAccountPrisonUUID = UUID.randomUUID()
      mockSubAccount(
        request.caseloadId,
        accountMapping.mapPrisonSubAccount(
          request.offenderTransactions[0].generalLedgerEntries[0].code,
          request.offenderTransactions[0].type,
        ),
        subAccountPrisonUUID,
      )

      val subAccountPrisonerUUID = UUID.randomUUID()
      mockSubAccount(
        offenderDisplayId,
        accountMapping.mapPrisonerSubAccount(
          request.offenderTransactions[0].generalLedgerEntries[1].code,
        ),
        subAccountPrisonerUUID,
      )

      val transactionRequestUUID = UUID.randomUUID()
      val mockTransaction = mockPostTransaction(
        request,
        listOf<GlPostingRequest>(
          GlPostingRequest(
            subAccountPrisonUUID,
            request.offenderTransactions[0].generalLedgerEntries[0].postingType.toGLPostingType(),
            request.offenderTransactions[0].generalLedgerEntries[0].amount.toPence(),
          ),
          GlPostingRequest(
            subAccountPrisonerUUID,
            request.offenderTransactions[0].generalLedgerEntries[1].postingType.toGLPostingType(),
            request.offenderTransactions[0].generalLedgerEntries[1].amount.toPence(),
          ),
        ),
        transactionRequestUUID,
      )

      val result = generalLedgerService.syncOffenderTransaction(request)

      assertThat(result).isEqualTo(transactionRequestUUID)

      verify(generalLedgerApiClient, times(1)).findAccountByReference(offenderDisplayId)
      verify(generalLedgerApiClient, times(1)).findAccountByReference(request.caseloadId)

      assertThat(result).isNotNull()

      verify(generalLedgerApiClient, times(1)).createAccount(offenderDisplayId)

      verify(generalLedgerApiClient, times(1)).postTransaction(eq(mockTransaction), any())
    }

    @Test
    fun `should create a new accounts when both prisoner and prison accounts are not found`() {
      val request = createRequest(offenderDisplayId)

      mockAccountNotFoundAndCreateAccount(offenderDisplayId)
      mockAccountNotFoundAndCreateAccount(request.caseloadId)

      val subAccountPrisonUUID = UUID.randomUUID()
      val subAccountPrisonerUUID = UUID.randomUUID()
      mockSubAccount(
        request.caseloadId,
        accountMapping.mapPrisonSubAccount(
          request.offenderTransactions[0].generalLedgerEntries[0].code,
          request.offenderTransactions[0].type,
        ),
        subAccountPrisonUUID,
      )

      mockSubAccount(
        offenderDisplayId,
        accountMapping.mapPrisonerSubAccount(
          request.offenderTransactions[0].generalLedgerEntries[1].code,
        ),
        subAccountPrisonerUUID,
      )

      val transactionRequestUUID = UUID.randomUUID()
      mockPostTransaction(
        request,
        listOf<GlPostingRequest>(
          GlPostingRequest(
            subAccountPrisonUUID,
            request.offenderTransactions[0].generalLedgerEntries[0].postingType.toGLPostingType(),
            request.offenderTransactions[0].generalLedgerEntries[0].amount.toPence(),
          ),
          GlPostingRequest(
            subAccountPrisonerUUID,
            request.offenderTransactions[0].generalLedgerEntries[1].postingType.toGLPostingType(),
            request.offenderTransactions[0].generalLedgerEntries[1].amount.toPence(),
          ),
        ),
        transactionRequestUUID,
      )

      val result = generalLedgerService.syncOffenderTransaction(request)
      assertThat(result).isEqualTo(transactionRequestUUID)

      verify(generalLedgerApiClient).findAccountByReference(request.caseloadId)
      verify(generalLedgerApiClient).findAccountByReference(offenderDisplayId)
      assertThat(result).isNotNull()

      val logs = listAppender.list.map { it.formattedMessage }

      verify(generalLedgerApiClient, times(1)).createAccount(offenderDisplayId)
      verify(generalLedgerApiClient, times(1)).createAccount(request.caseloadId)

      assertThat(logs).contains("General Ledger account not found for '${request.caseloadId}'. Creating new account.")
      assertThat(logs).contains("General Ledger account not found for '$offenderDisplayId'. Creating new account.")
    }

    @Test
    fun `should propagate exception from findAccountByReference`() {
      val request = mock<SyncOffenderTransactionRequest>()
      whenever(request.caseloadId).thenReturn("TES")

      val expectedError = RuntimeException("Network Error")

      whenever(generalLedgerApiClient.findAccountByReference(request.caseloadId))
        .thenThrow(expectedError)

      assertThatThrownBy {
        generalLedgerService.syncOffenderTransaction(request)
      }.isEqualTo(expectedError)
    }

    private fun createRequest(offenderDisplayId: String, prisonCode: String = "TES", glEntries: List<GeneralLedgerEntry> = emptyList()): SyncOffenderTransactionRequest {
      val offenderTx = mock<OffenderTransaction>()
      whenever(offenderTx.type).thenReturn("CANT")
      whenever(offenderTx.offenderDisplayId).thenReturn(offenderDisplayId)
      whenever(offenderTx.description).thenReturn("description text")
      whenever(offenderTx.reference).thenReturn("reference text")

      if (glEntries.isNotEmpty()) {
        whenever(offenderTx.generalLedgerEntries).thenReturn(glEntries)
      } else {
        whenever(offenderTx.generalLedgerEntries).thenReturn(
          listOf(
            GeneralLedgerEntry(1, 1501, "CR", 10.00),
            GeneralLedgerEntry(2, 2101, "DR", 10.00),
          ),
        )
      }

      val request = mock<SyncOffenderTransactionRequest>()
      whenever(request.offenderTransactions).thenReturn(listOf(offenderTx))
      whenever(request.caseloadId).thenReturn(prisonCode)
      whenever(request.transactionTimestamp).thenReturn(LocalDateTime.now())

      return request
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

      val accountUUIDPrison = UUID.randomUUID()

      mockAccount(requestWithNoOffenderTransactions.caseloadId, accountUUIDPrison)

      assertThatThrownBy {
        generalLedgerService.syncOffenderTransaction(requestWithNoOffenderTransactions)
      }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `should call POST transaction`() {
      val transactionId = Random.nextLong(10000, 99999)
      val timestamp = LocalDateTime.now()
      val prisonId = "LEI"
      val amount = 5.00

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

      val accountUUIDPrisoner = UUID.randomUUID()
      val accountUUIDPrison = UUID.randomUUID()

      val subAccountUUIDPrisoner = UUID.randomUUID()
      val subAccountUUIDPrison = UUID.randomUUID()

      mockAccount(request.caseloadId, accountUUIDPrison)

      mockAccount(offenderDisplayId, accountUUIDPrisoner)

      mockSubAccount(
        prisonId,
        accountMapping.mapPrisonSubAccount(prisonAccountCode, transactionType),
        subAccountUUIDPrison,
      )

      mockSubAccount(
        offenderDisplayId,
        accountMapping.mapPrisonerSubAccount(prisonerAccountCode),
        subAccountUUIDPrisoner,
      )

      val glTransactionRequest = GlTransactionRequest(
        reference = request.offenderTransactions[0].reference!!,
        description = request.offenderTransactions[0].description,
        timestamp = request.transactionTimestamp.toInstant(ZoneOffset.UTC),
        amount = amount.toPence(),
        postings = listOf(
          GlPostingRequest(subAccountUUIDPrison, PostingType.CR, amount.toPence()),
          GlPostingRequest(subAccountUUIDPrisoner, PostingType.DR, amount.toPence()),
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
      val amount = 5.00

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
            entrySequence = 1,
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

      val accountUUIDPrisoner1 = UUID.randomUUID()
      val accountUUIDPrisoner2 = UUID.randomUUID()
      val accountUUIDPrison = UUID.randomUUID()

      val subAccountUUIDPrisoner1 = UUID.randomUUID()
      val subAccountUUIDPrisoner2 = UUID.randomUUID()
      val subAccountUUIDPrison = UUID.randomUUID()

      mockAccount(requestTransactionWithMultiplePrisoners.caseloadId, accountUUIDPrison)

      mockAccount(prisoner1DisplayId, accountUUIDPrisoner1)

      mockAccount(prisoner2DisplayId, accountUUIDPrisoner2)

      mockSubAccount(
        prisonId,
        accountMapping.mapPrisonSubAccount(prisonAccountCode, transactionType),
        subAccountUUIDPrison,
      )

      mockSubAccount(
        prisoner1DisplayId,
        accountMapping.mapPrisonerSubAccount(prisonerAccountCode),
        subAccountUUIDPrisoner1,
      )

      mockSubAccount(
        prisoner2DisplayId,
        accountMapping.mapPrisonerSubAccount(prisonerAccountCode),
        subAccountUUIDPrisoner2,
      )

      val glTransactionRequestPrisoner1 = GlTransactionRequest(
        reference = requestTransactionWithMultiplePrisoners.offenderTransactions[0].reference!!,
        description = requestTransactionWithMultiplePrisoners.offenderTransactions[0].description,
        timestamp = requestTransactionWithMultiplePrisoners.transactionTimestamp.toInstant(ZoneOffset.UTC),
        amount = amount.toPence(),
        postings = listOf(
          GlPostingRequest(subAccountUUIDPrison, PostingType.CR, amount.toPence()),
          GlPostingRequest(subAccountUUIDPrisoner1, PostingType.DR, amount.toPence()),
        ),
      )

      val glTransactionRequestPrisoner2 = GlTransactionRequest(
        reference = requestTransactionWithMultiplePrisoners.offenderTransactions[0].reference!!,
        description = requestTransactionWithMultiplePrisoners.offenderTransactions[0].description,
        timestamp = requestTransactionWithMultiplePrisoners.transactionTimestamp.toInstant(ZoneOffset.UTC),
        amount = amount.toPence(),
        postings = listOf(
          GlPostingRequest(subAccountUUIDPrison, PostingType.CR, amount.toPence()),
          GlPostingRequest(subAccountUUIDPrisoner2, PostingType.DR, amount.toPence()),
        ),
      )
      whenever(generalLedgerApiClient.postTransaction(eq(glTransactionRequestPrisoner1), any())).thenReturn(UUID.randomUUID())
      whenever(generalLedgerApiClient.postTransaction(eq(glTransactionRequestPrisoner2), any())).thenReturn(UUID.randomUUID())

      generalLedgerService.syncOffenderTransaction(requestTransactionWithMultiplePrisoners)

      verify(generalLedgerApiClient).postTransaction(eq(glTransactionRequestPrisoner1), any())
      verify(generalLedgerApiClient).postTransaction(eq(glTransactionRequestPrisoner2), any())
    }
  }

  @Nested
  @DisplayName("syncOffenderTransactionSubAccount")
  inner class SyncOffenderTransactionSubAccount {

    fun makeMockTransactions(
      prisonerDisplayId: String,
      prisonCode: String,
      transactionType: String,
      transactionEntries: List<GeneralLedgerEntry>,
    ): SyncOffenderTransactionRequest {
      val transaction =
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1L,
          offenderDisplayId = prisonerDisplayId,
          offenderBookingId = 100L,
          subAccountType = "SPND",
          postingType = "DR",
          type = transactionType,
          description = "Test Transaction",
          amount = 10.00,
          reference = "REF",
          generalLedgerEntries = transactionEntries,
        )

      val syncOffenderTransactionRequest = mock<SyncOffenderTransactionRequest>()
      whenever(syncOffenderTransactionRequest.offenderTransactions).thenReturn(listOf(transaction))
      whenever(syncOffenderTransactionRequest.transactionTimestamp).thenReturn(LocalDateTime.now())

      whenever(syncOffenderTransactionRequest.caseloadId).thenReturn(prisonCode)

      return syncOffenderTransactionRequest
    }

    @Test
    fun `should find prisoner SUB account when prisoner account exists`() {
      val prisonerDisplayId = "A123456"
      val prisonCode = "MDI"

      val syncOffenderTransactionRequest = makeMockTransactions(
        prisonerDisplayId,
        prisonCode,
        "TEST",
        listOf(
          GeneralLedgerEntry(1, 2102, "DR", 10.00),
          GeneralLedgerEntry(2, 2101, "CR", 10.00),
        ),
      )
      mockAccount(prisonerDisplayId)
      mockAccount(prisonCode)

      syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries.forEach { entry ->
        mockSubAccount(
          prisonerDisplayId,
          accountMapping.mapPrisonerSubAccount(entry.code),
        )
      }

      generalLedgerService.syncOffenderTransaction(syncOffenderTransactionRequest)

      verify(generalLedgerApiClient, times(1)).findSubAccount(
        prisonerDisplayId,
        accountMapping.mapPrisonerSubAccount(
          syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[0].code,
        ),
      )
      verify(generalLedgerApiClient, times(1)).findSubAccount(
        prisonerDisplayId,
        accountMapping.mapPrisonerSubAccount(
          syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[1].code,
        ),
      )
    }

    @Test
    fun `should find prison SUB account when prison sub account exists`() {
      val prisonerDisplayId = "A123456"
      val prisonCode = "MDI"
      val transactionType = "CANT"
      val prisonerSubAccount = 2101
      val prisonSubAccount = 1502

      val syncOffenderTransactionRequest = makeMockTransactions(
        prisonerDisplayId,
        prisonCode,
        transactionType,
        listOf(
          GeneralLedgerEntry(1, prisonSubAccount, "DR", 10.00),
          GeneralLedgerEntry(2, prisonerSubAccount, "CR", 10.00),
        ),
      )

      mockAccount(prisonerDisplayId)
      mockAccount(prisonCode)
      mockSubAccount(
        prisonCode,
        accountMapping.mapPrisonSubAccount(prisonSubAccount, transactionType),
      )
      mockSubAccount(
        prisonerDisplayId,
        accountMapping.mapPrisonerSubAccount(prisonerSubAccount),
      )

      generalLedgerService.syncOffenderTransaction(syncOffenderTransactionRequest)

      verify(generalLedgerApiClient, times(1)).findSubAccount(
        prisonCode,
        accountMapping.mapPrisonSubAccount(
          syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[0].code,
          transactionType,
        ),
      )
    }

    @Test
    fun `should find prisoner and prison SUB accounts when prisoner and prison sub accounts exist`() {
      val prisonerDisplayId = "A123456"
      val prisonCode = "MDI"
      val transactionType = "CANT"
      val prisonerSubAccount = 2101
      val prisonSubAccount = 1502

      val syncOffenderTransactionRequest = makeMockTransactions(
        prisonerDisplayId,
        prisonCode,
        transactionType,
        listOf(
          GeneralLedgerEntry(1, prisonSubAccount, "DR", 10.00),
          GeneralLedgerEntry(2, prisonerSubAccount, "CR", 10.00),
        ),
      )

      mockAccount(prisonerDisplayId)
      mockAccount(prisonCode)

      mockSubAccount(
        prisonCode,
        accountMapping.mapPrisonSubAccount(prisonSubAccount, transactionType),
      )
      mockSubAccount(
        prisonerDisplayId,
        accountMapping.mapPrisonerSubAccount(prisonerSubAccount),
      )

      generalLedgerService.syncOffenderTransaction(syncOffenderTransactionRequest)

      verify(generalLedgerApiClient, times(1)).findSubAccount(
        prisonCode,
        accountMapping.mapPrisonSubAccount(
          syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[0].code,
          transactionType,
        ),
      )
      verify(generalLedgerApiClient, times(1)).findSubAccount(
        prisonerDisplayId,
        accountMapping.mapPrisonerSubAccount(
          syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[1].code,
        ),
      )

      verify(generalLedgerApiClient, times(0)).createSubAccount(
        any(),
        any(),
      )
    }

    @Test
    fun `should find and create prisoner SUB account when prisoner sub account doesnt exist`() {
      val prisonerDisplayId = "A123456"
      val prisonCode = "MDI"

      val syncOffenderTransactionRequest = makeMockTransactions(
        prisonerDisplayId,
        prisonCode,
        "TEST",
        listOf(
          GeneralLedgerEntry(1, 2102, "DR", 10.00),
          GeneralLedgerEntry(2, 2101, "CR", 10.00),
        ),
      )

      val parentUUID = UUID.randomUUID()
      mockAccount(prisonerDisplayId, parentUUID)
      mockAccount(prisonCode)

      val subAccountRefs = mutableListOf<String>()
      syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries.forEach { entry ->
        val prisonerSubAccountRef = accountMapping.mapPrisonerSubAccount(entry.code)
        subAccountRefs.add(prisonerSubAccountRef)
        mockSubAccountNotFoundAndCreateSubAccount(prisonerDisplayId, parentUUID, prisonerSubAccountRef)
      }

      generalLedgerService.syncOffenderTransaction(syncOffenderTransactionRequest)

      subAccountRefs.forEach { ref ->
        verify(generalLedgerApiClient, times(1)).findSubAccount(
          prisonerDisplayId,
          ref,
        )
        verify(generalLedgerApiClient, times(1)).createSubAccount(
          parentUUID,
          ref,
        )
      }
    }

    @Test
    fun `should find and create prison SUB account when prison sub account doesnt exist`() {
      val prisonerDisplayId = "A123456"
      val prisonCode = "MDI"
      val transactionType = "CANT"

      val syncOffenderTransactionRequest = makeMockTransactions(
        prisonerDisplayId,
        prisonCode,
        transactionType,
        listOf(
          GeneralLedgerEntry(1, 1502, "DR", 10.00),
          GeneralLedgerEntry(2, 2101, "CR", 10.00),
        ),
      )

      val parentUUIDPrison = UUID.randomUUID()
      mockAccount(prisonerDisplayId)
      mockAccount(prisonCode, parentUUIDPrison)

      mockSubAccount(
        prisonerDisplayId,
        accountMapping.mapPrisonerSubAccount(
          syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[1].code,
        ),
      )

      val prisonSubAccountRef = accountMapping.mapPrisonSubAccount(
        syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[0].code,
        transactionType,
      )

      mockSubAccountNotFoundAndCreateSubAccount(
        prisonCode,
        parentUUIDPrison,
        prisonSubAccountRef,
      )

      generalLedgerService.syncOffenderTransaction(syncOffenderTransactionRequest)

      verify(generalLedgerApiClient, times(1)).findSubAccount(
        prisonerDisplayId,
        accountMapping.mapPrisonerSubAccount(
          syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[1].code,
        ),
      )

      verify(generalLedgerApiClient, times(1)).createSubAccount(
        parentUUIDPrison,
        prisonSubAccountRef,
      )
    }
  }

  @Nested
  @DisplayName("reconcilePrisonerBalances")
  inner class ReconcilePrisonerBalances {

    val prisonNumber = "A1234AA"

    @Test
    fun `should calculate legacy balances when called`() {
      generalLedgerService.reconcilePrisoner(prisonNumber)

      verify(ledgerQueryService).aggregatedLegacyBalanceByPrisoner(prisonNumber)
    }
  }
}
