package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers.sync.SyncController
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.SyncQueryService
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.SyncService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SyncControllerTest {

  @Mock
  private lateinit var syncService: SyncService

  @Mock
  private lateinit var syncQueryService: SyncQueryService

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
      val receipt = createReceipt(SyncTransactionReceipt.Action.CREATED)
      `when`(syncService.syncTransaction(any<SyncOffenderTransactionRequest>())).thenReturn(receipt)
      val response = syncController.postOffenderTransaction(request)
      assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
      assertThat(response.body).isEqualTo(receipt)
    }

    @Test
    fun `should return OK when transaction is updated`() {
      val request = createOffenderTransactionRequest()
      val receipt = createReceipt(SyncTransactionReceipt.Action.UPDATED)
      `when`(syncService.syncTransaction(any<SyncOffenderTransactionRequest>())).thenReturn(receipt)
      val response = syncController.postOffenderTransaction(request)
      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(receipt)
    }

    @Test
    fun `should return OK when transaction is processed`() {
      val request = createOffenderTransactionRequest()
      val receipt = createReceipt(SyncTransactionReceipt.Action.PROCESSED)
      `when`(syncService.syncTransaction(any<SyncOffenderTransactionRequest>())).thenReturn(receipt)
      val response = syncController.postOffenderTransaction(request)
      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(receipt)
    }
  }

  @Nested
  @DisplayName("postGeneralLedgerTransaction")
  inner class PostGeneralLedgerTransaction {
    @Test
    fun `should return CREATED when general ledger transaction is new`() {
      val request = createGeneralLedgerTransactionRequest()
      val receipt = createReceipt(SyncTransactionReceipt.Action.CREATED)
      `when`(syncService.syncTransaction(any<SyncGeneralLedgerTransactionRequest>())).thenReturn(receipt)
      val response = syncController.postGeneralLedgerTransaction(request)
      assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
      assertThat(response.body).isEqualTo(receipt)
    }

    @Test
    fun `should return OK when general ledger transaction is updated`() {
      val request = createGeneralLedgerTransactionRequest()
      val receipt = createReceipt(SyncTransactionReceipt.Action.UPDATED)
      `when`(syncService.syncTransaction(any<SyncGeneralLedgerTransactionRequest>())).thenReturn(receipt)
      val response = syncController.postGeneralLedgerTransaction(request)
      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(receipt)
    }

    @Test
    fun `should return OK when general ledger transaction is processed`() {
      val request = createGeneralLedgerTransactionRequest()
      val receipt = createReceipt(SyncTransactionReceipt.Action.PROCESSED)
      `when`(syncService.syncTransaction(any<SyncGeneralLedgerTransactionRequest>())).thenReturn(receipt)
      val response = syncController.postGeneralLedgerTransaction(request)
      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(receipt)
    }
  }

  @Nested
  @DisplayName("getGeneralLedgerTransactionsByDate")
  inner class GetGeneralLedgerTransactionsByDate {
    @Test
    fun `should return a list of transactions and OK status with pagination data`() {
      val startDate = LocalDate.of(2025, 1, 1)
      val endDate = LocalDate.of(2025, 1, 31)
      val transactions = listOf(generalLedgerTransactionResponse)
      val page = PageImpl(transactions, org.springframework.data.domain.PageRequest.of(0, 20), 1)

      `when`(syncQueryService.getGeneralLedgerTransactionsByDate(startDate, endDate, 0, 20)).thenReturn(page)
      val response = syncController.getGeneralLedgerTransactionsByDate(startDate, endDate, 0, 20)

      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isInstanceOf(SyncGeneralLedgerTransactionListResponse::class.java)
      assertThat(response.body?.transactions).hasSize(1)
      assertThat(response.body?.transactions).isEqualTo(transactions)
      assertThat(response.body?.page).isEqualTo(0)
      assertThat(response.body?.totalElements).isEqualTo(1)
      assertThat(response.body?.totalPages).isEqualTo(1)
      assertThat(response.body?.last).isTrue()
    }

    @Test
    fun `should return an empty list and OK status when no transactions are found`() {
      val startDate = LocalDate.of(2025, 1, 1)
      val endDate = LocalDate.of(2025, 1, 31)
      val emptyPage = Page.empty<SyncGeneralLedgerTransactionResponse>()

      `when`(syncQueryService.getGeneralLedgerTransactionsByDate(startDate, endDate, 0, 20)).thenReturn(emptyPage)
      val response = syncController.getGeneralLedgerTransactionsByDate(startDate, endDate, 0, 20)

      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isInstanceOf(SyncGeneralLedgerTransactionListResponse::class.java)
      assertThat(response.body?.transactions).isEmpty()
      assertThat(response.body?.totalElements).isEqualTo(0)
    }
  }

  @Nested
  @DisplayName("getOffenderTransactionsByDate")
  inner class GetOffenderTransactionsByDate {
    @Test
    fun `should return a list of offender transactions and OK status with pagination data`() {
      val startDate = LocalDate.of(2025, 1, 1)
      val endDate = LocalDate.of(2025, 1, 31)
      val transactions = listOf(offenderTransactionResponse)
      val page = PageImpl(transactions, org.springframework.data.domain.PageRequest.of(0, 20), 1)

      `when`(syncQueryService.getOffenderTransactionsByDate(startDate, endDate, 0, 20)).thenReturn(page)
      val response = syncController.getOffenderTransactionsByDate(startDate, endDate, 0, 20)

      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isInstanceOf(SyncOffenderTransactionListResponse::class.java)
      assertThat(response.body?.offenderTransactions).hasSize(1)
      assertThat(response.body?.offenderTransactions).isEqualTo(transactions)
      assertThat(response.body?.page).isEqualTo(0)
      assertThat(response.body?.totalElements).isEqualTo(1)
      assertThat(response.body?.totalPages).isEqualTo(1)
      assertThat(response.body?.last).isTrue()
    }

    @Test
    fun `should return an empty list and OK status when no offender transactions are found`() {
      val startDate = LocalDate.of(2025, 1, 1)
      val endDate = LocalDate.of(2025, 1, 31)
      val emptyPage = Page.empty<SyncOffenderTransactionResponse>()

      `when`(syncQueryService.getOffenderTransactionsByDate(startDate, endDate, 0, 20)).thenReturn(emptyPage)
      val response = syncController.getOffenderTransactionsByDate(startDate, endDate, 0, 20)

      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isInstanceOf(SyncOffenderTransactionListResponse::class.java)
      assertThat(response.body?.offenderTransactions).isEmpty()
      assertThat(response.body?.totalElements).isEqualTo(0)
    }
  }

  @Nested
  @DisplayName("getGeneralLedgerTransactionById")
  inner class GetGeneralLedgerTransactionById {
    @Test
    fun `should return OK and the transaction when found`() {
      val id = generalLedgerTransactionResponse.synchronizedTransactionId
      `when`(syncQueryService.getGeneralLedgerTransactionById(id)).thenReturn(generalLedgerTransactionResponse)
      val response = syncController.getGeneralLedgerTransactionById(id)
      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(generalLedgerTransactionResponse)
    }

    @Test
    fun `should return NOT_FOUND when transaction is not found`() {
      val id = UUID.randomUUID()
      `when`(syncQueryService.getGeneralLedgerTransactionById(id)).thenReturn(null)
      val response = syncController.getGeneralLedgerTransactionById(id)
      assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
      assertThat(response.body).isInstanceOf(ErrorResponse::class.java)
      val errorResponse = response.body as ErrorResponse
      assertThat(errorResponse.status).isEqualTo(404)
    }
  }

  @Nested
  @DisplayName("getOffenderTransactionById")
  inner class GetOffenderTransactionById {
    @Test
    fun `should return OK and the transaction when found`() {
      val id = offenderTransactionResponse.synchronizedTransactionId
      `when`(syncQueryService.getOffenderTransactionById(id)).thenReturn(offenderTransactionResponse)
      val response = syncController.getOffenderTransactionById(id)
      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(offenderTransactionResponse)
    }

    @Test
    fun `should return NOT_FOUND when transaction is not found`() {
      val id = UUID.randomUUID()
      `when`(syncQueryService.getOffenderTransactionById(id)).thenReturn(null)
      val response = syncController.getOffenderTransactionById(id)
      assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
      assertThat(response.body).isInstanceOf(ErrorResponse::class.java)
      val errorResponse = response.body as ErrorResponse
      assertThat(errorResponse.status).isEqualTo(404)
    }
  }
}
