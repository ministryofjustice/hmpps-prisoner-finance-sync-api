package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.sync.SyncController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.GeneralLedgerService.SyncOffenderTransactionToGeneralLedgerResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.sync.SyncPayloadCaptureService
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SyncControllerTest {
  @Mock
  private lateinit var generalLedgerService: GeneralLedgerService

  @Mock
  private lateinit var syncPayloadCaptureService: SyncPayloadCaptureService

  @InjectMocks
  private lateinit var syncController: SyncController

  private lateinit var offenderTransactionResponse: SyncOffenderTransactionResponse
  private lateinit var generalLedgerTransactionResponse: SyncGeneralLedgerTransactionResponse

  private fun createOffenderTransactionRequest() = SyncOffenderTransactionRequest(
    transactionId = 19228028,
    requestId = UUID.randomUUID(),
    caseloadId = "GMI",
    transactionTimestamp = LocalDateTime.now(),
    createdAt = LocalDateTime.now(),
    createdBy = "JD12345",
    createdByDisplayName = "J Doe",
    lastModifiedAt = null,
    lastModifiedBy = null,
    lastModifiedByDisplayName = null,
    offenderTransactions = listOf(
      OffenderTransaction(
        entrySequence = 1,
        offenderId = 1015388L,
        offenderDisplayId = "AA001AA",
        offenderBookingId = 455987L,
        subAccountType = "REG",
        postingType = "DR",
        type = "OT",
        description = "Sub-Account Transfer",
        amount = BigDecimal("162.00"),
        reference = null,
        generalLedgerEntries = listOf(
          GeneralLedgerEntry(entrySequence = 1, code = 2101, postingType = "DR", amount = BigDecimal("162.00")),
          GeneralLedgerEntry(entrySequence = 2, code = 2102, postingType = "CR", amount = BigDecimal("162.00")),
        ),
      ),
    ),
  )

  private fun createGeneralLedgerTransactionRequest() = SyncGeneralLedgerTransactionRequest(
    transactionId = 19228028,
    requestId = UUID.randomUUID(),
    description = "General Ledger Account Transfer",
    reference = "REF12345",
    caseloadId = "GMI",
    transactionType = "GJ",
    transactionTimestamp = LocalDateTime.now(),
    createdAt = LocalDateTime.now(),
    createdBy = "JD12345",
    createdByDisplayName = "J Doe",
    lastModifiedAt = null,
    lastModifiedBy = null,
    lastModifiedByDisplayName = null,
    generalLedgerEntries = listOf(
      GeneralLedgerEntry(entrySequence = 1, code = 1101, postingType = "DR", amount = BigDecimal("50.00")),
      GeneralLedgerEntry(entrySequence = 2, code = 2503, postingType = "CR", amount = BigDecimal("50.00")),
    ),
  )

  private fun createReceipt(action: SyncTransactionReceipt.Action) = SyncTransactionReceipt(
    requestId = UUID.randomUUID(),
    synchronizedTransactionId = UUID.randomUUID(),
    action = action,
  )

  @BeforeEach
  fun setup() {
    offenderTransactionResponse = SyncOffenderTransactionResponse(
      synchronizedTransactionId = UUID.randomUUID(),
      legacyTransactionId = 123L,
      caseloadId = "GMI",
      transactionTimestamp = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      createdBy = "TestUser",
      createdByDisplayName = "Test User",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      transactions = emptyList(),
    )

    generalLedgerTransactionResponse = SyncGeneralLedgerTransactionResponse(
      synchronizedTransactionId = UUID.randomUUID(),
      legacyTransactionId = 456L,
      description = "Test Transaction",
      reference = "REF123",
      caseloadId = "GMI",
      transactionType = "GJ",
      transactionTimestamp = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      createdBy = "TestUser",
      createdByDisplayName = "Test User",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      generalLedgerEntries = emptyList(),
    )
  }

  @Nested
  @DisplayName("postOffenderTransaction")
  inner class PostOffenderTransaction {
    @Test
    fun `should return CREATED when transaction is new`() {
      val request = createOffenderTransactionRequest()
      whenever { generalLedgerService.syncOffenderTransaction(any()) }.thenReturn(
        SyncOffenderTransactionToGeneralLedgerResponse(
          previouslyMappedTransactionEntries = emptyList(),
          unsuccessfullyMappedTransactionEntries = emptyList(),
          successfullyMappedTransactionEntries = listOf(mock()),
        ),
      )

      val response = syncController.postOffenderTransaction(request)
      assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
      assertThat(response.body?.action).isEqualTo(SyncTransactionReceipt.Action.CREATED)
    }

    @Test
    fun `should return OK when transaction is sent without updates`() {
      val request = createOffenderTransactionRequest()
      whenever { generalLedgerService.syncOffenderTransaction(any()) }.thenReturn(
        SyncOffenderTransactionToGeneralLedgerResponse(
          previouslyMappedTransactionEntries = listOf(mock()),
          unsuccessfullyMappedTransactionEntries = emptyList(),
          successfullyMappedTransactionEntries = emptyList(),
        ),
      )

      val response = syncController.postOffenderTransaction(request)
      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body?.action).isEqualTo(SyncTransactionReceipt.Action.PROCESSED)
    }

    @Test
    fun `should return OK when transaction contains transactions that were previously processed and new ones`() {
      val request = createOffenderTransactionRequest()
      whenever { generalLedgerService.syncOffenderTransaction(any()) }.thenReturn(
        SyncOffenderTransactionToGeneralLedgerResponse(
          previouslyMappedTransactionEntries = listOf(mock()),
          unsuccessfullyMappedTransactionEntries = emptyList(),
          successfullyMappedTransactionEntries = listOf(mock()),
        ),
      )

      val response = syncController.postOffenderTransaction(request)
      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body?.action).isEqualTo(SyncTransactionReceipt.Action.PROCESSED)
    }

    @Test
    fun `should return UNPROCESSABLE_ENTITY when transaction is processed with errors`() {
      val request = createOffenderTransactionRequest()
      whenever { generalLedgerService.syncOffenderTransaction(any()) }.thenReturn(
        SyncOffenderTransactionToGeneralLedgerResponse(
          previouslyMappedTransactionEntries = listOf(mock()),
          unsuccessfullyMappedTransactionEntries = listOf(mock()),
          successfullyMappedTransactionEntries = listOf(mock()),
        ),
      )

      val response = syncController.postOffenderTransaction(request)
      assertThat(response.statusCode).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
      assertThat(response.body?.action).isEqualTo(SyncTransactionReceipt.Action.PROCESSED_WITH_ERRORS)
    }
  }
}
