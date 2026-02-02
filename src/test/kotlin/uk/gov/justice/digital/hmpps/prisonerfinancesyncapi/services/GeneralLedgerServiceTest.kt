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
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlSubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GeneralLedgerServiceTest {

  @Mock
  private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @InjectMocks
  private lateinit var generalLedgerService: GeneralLedgerService

  @Spy
  private lateinit var accountMapping: LedgerAccountMappingService

  private lateinit var listAppender: ListAppender<ILoggingEvent>

  private val logger = LoggerFactory.getLogger(GeneralLedgerService::class.java) as Logger

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

    private val offenderDisplayId = "A1234AA"

    @Test
    fun `should log accountId when existing prison and prisoner account found`() {
      val request = createOffenderRequest(offenderDisplayId)
      val accountUuid = UUID.randomUUID()

      var mockGLAccountResponse = mock<GlAccountResponse>()
      whenever(mockGLAccountResponse.id).thenReturn(accountUuid)

      whenever(generalLedgerApiClient.findAccountByReference(request.caseloadId))
        .thenReturn(mockGLAccountResponse)

      whenever(generalLedgerApiClient.findAccountByReference(offenderDisplayId)).thenReturn(mockGLAccountResponse)

      val result = generalLedgerService.syncOffenderTransaction(request)

      verify(generalLedgerApiClient).findAccountByReference(offenderDisplayId)
      assertThat(result).isNotNull()

      val logs = listAppender.list.map { it.formattedMessage }
      assertThat(logs).anyMatch { it.contains("General Ledger account found for '$offenderDisplayId' (UUID: $accountUuid)") }
      assertThat(logs).contains("General Ledger account found for '${request.caseloadId}' (UUID: $accountUuid)")
      assertThat(logs).contains("General Ledger account found for '$offenderDisplayId' (UUID: $accountUuid)")
    }

    @Test
    fun `should create parent account for prison when account not found`() {
      val request = createOffenderRequest(offenderDisplayId)
      whenever(generalLedgerApiClient.findAccountByReference(offenderDisplayId)).thenReturn(null)

      whenever(generalLedgerApiClient.createAccount(offenderDisplayId))
        .thenReturn(mock<GlAccountResponse>())

      whenever(generalLedgerApiClient.findAccountByReference(request.caseloadId)).thenReturn(null)

      whenever(generalLedgerApiClient.createAccount(request.caseloadId))
        .thenReturn(mock<GlAccountResponse>())

      val result = generalLedgerService.syncOffenderTransaction(request)

      verify(generalLedgerApiClient, times(1)).findAccountByReference(request.caseloadId)
      assertThat(result).isNotNull()

      verify(generalLedgerApiClient, times(1)).createAccount(request.caseloadId)
    }

    @Test
    fun `should create parent account for prisoner when account not found`() {
      val request = createOffenderRequest(offenderDisplayId)
      whenever(generalLedgerApiClient.findAccountByReference(request.caseloadId)).thenReturn(null)

      whenever(generalLedgerApiClient.createAccount(offenderDisplayId))
        .thenReturn(mock<GlAccountResponse>())

      whenever(generalLedgerApiClient.findAccountByReference(request.caseloadId)).thenReturn(null)

      whenever(generalLedgerApiClient.createAccount(request.caseloadId))
        .thenReturn(mock<GlAccountResponse>())

      val result = generalLedgerService.syncOffenderTransaction(request)

      verify(generalLedgerApiClient, times(1)).findAccountByReference(offenderDisplayId)
      verify(generalLedgerApiClient, times(1)).findAccountByReference(request.caseloadId)

      assertThat(result).isNotNull()

      verify(generalLedgerApiClient, times(1)).createAccount(offenderDisplayId)
    }

    @Test
    fun `should create a new accounts when both prisoner and prison accounts are not found`() {
      val request = createOffenderRequest(offenderDisplayId)
      whenever(generalLedgerApiClient.findAccountByReference(offenderDisplayId))
        .thenReturn(null)
      whenever(generalLedgerApiClient.findAccountByReference(request.caseloadId))
        .thenReturn(null)

      whenever(generalLedgerApiClient.createAccount(offenderDisplayId))
        .thenReturn(mock<GlAccountResponse>())
      whenever(generalLedgerApiClient.createAccount(request.caseloadId))
        .thenReturn(mock<GlAccountResponse>())

      val result = generalLedgerService.syncOffenderTransaction(request)

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

      whenever(generalLedgerApiClient.findAccountByReference(any()))
        .thenThrow(expectedError)

      assertThatThrownBy {
        generalLedgerService.syncOffenderTransaction(request)
      }.isEqualTo(expectedError)
    }

    private fun createOffenderRequest(offenderDisplayId: String, prisonCode: String = "TES"): SyncOffenderTransactionRequest {
      val offenderTx = mock<OffenderTransaction>()
      whenever(offenderTx.offenderDisplayId).thenReturn(offenderDisplayId)

      val request = mock<SyncOffenderTransactionRequest>()
      whenever(request.offenderTransactions).thenReturn(listOf(offenderTx))
      whenever(request.caseloadId).thenReturn(prisonCode)

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

      whenever(syncOffenderTransactionRequest.caseloadId).thenReturn(prisonCode)

      return syncOffenderTransactionRequest
    }

    @Test
    fun `should find prisoner SUB account`() {
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

      val glResponse = mock<GlAccountResponse>()
      whenever(glResponse.id).thenReturn(UUID.randomUUID())

      whenever(generalLedgerApiClient.findAccountByReference(prisonerDisplayId))
        .thenReturn(glResponse)

      whenever(generalLedgerApiClient.findAccountByReference(prisonCode))
        .thenReturn(mock<GlAccountResponse>())

      whenever(
        generalLedgerApiClient.findSubAccount(
          any(),
          any(),
        ),
      ).thenReturn(mock<GlSubAccountResponse>())

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
    fun `should find prison SUB account`() {
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

      val glResponsePrisoner = mock<GlAccountResponse>()
      whenever(glResponsePrisoner.id).thenReturn(UUID.randomUUID())

      whenever(generalLedgerApiClient.findAccountByReference(prisonerDisplayId))
        .thenReturn(glResponsePrisoner)

      val glResponsePrison = mock<GlAccountResponse>()

      whenever(glResponsePrison.id).thenReturn(UUID.randomUUID())

      whenever(generalLedgerApiClient.findAccountByReference(prisonCode))
        .thenReturn(glResponsePrison)

      whenever(
        generalLedgerApiClient.findSubAccount(
          any(),
          any(),
        ),
      ).thenReturn(mock<GlSubAccountResponse>())

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
    }

    @Test
    fun `should find and create prisoner SUB account`() {
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

      val glResponsePrisoner = mock<GlAccountResponse>()
      whenever(glResponsePrisoner.id).thenReturn(UUID.randomUUID())

      whenever(generalLedgerApiClient.findAccountByReference(prisonerDisplayId))
        .thenReturn(glResponsePrisoner)

      whenever(generalLedgerApiClient.findAccountByReference(prisonCode))
        .thenReturn(mock<GlAccountResponse>())

      whenever(
        generalLedgerApiClient.findSubAccount(
          prisonerDisplayId,
          accountMapping.mapPrisonerSubAccount(
            syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[0].code,
          ),
        ),
      ).thenReturn(null)

      generalLedgerService.syncOffenderTransaction(syncOffenderTransactionRequest)

      val prisonSubAccountRef = accountMapping.mapPrisonerSubAccount(
        syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[1].code,
      )

      verify(generalLedgerApiClient, times(1)).findSubAccount(
        prisonerDisplayId,
        prisonSubAccountRef,
      )

      verify(generalLedgerApiClient, times(1)).createSubAccount(
        glResponsePrisoner.id,
        prisonSubAccountRef,
      )
    }

    @Test
    fun `should find and create prison SUB account`() {
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

      val glResponsePrisoner = mock<GlAccountResponse>()
      whenever(glResponsePrisoner.id).thenReturn(UUID.randomUUID())

      whenever(generalLedgerApiClient.findAccountByReference(prisonerDisplayId))
        .thenReturn(glResponsePrisoner)

      val glResponsePrison = mock<GlAccountResponse>()

      whenever(glResponsePrison.id).thenReturn(UUID.randomUUID())

      whenever(generalLedgerApiClient.findAccountByReference(prisonCode))
        .thenReturn(glResponsePrison)

      val prisonSubAccountRef = accountMapping.mapPrisonSubAccount(
        syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[0].code,
        transactionType,
      )

      whenever(
        generalLedgerApiClient.findSubAccount(
          prisonCode,
          prisonSubAccountRef,
        ),
      ).thenReturn(null)

      generalLedgerService.syncOffenderTransaction(syncOffenderTransactionRequest)

      verify(generalLedgerApiClient, times(1)).findSubAccount(
        prisonerDisplayId,
        accountMapping.mapPrisonerSubAccount(
          syncOffenderTransactionRequest.offenderTransactions[0].generalLedgerEntries[1].code,
        ),
      )

      verify(generalLedgerApiClient, times(1)).createSubAccount(
        glResponsePrison.id,
        prisonSubAccountRef,
      )
    }
  }
}
