package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.AdvancesApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AdvancesMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AdvancesServiceTest {

  @Mock
  lateinit var advancesApiClient: AdvancesApiClient

  @Spy
  lateinit var timeConversionService: TimeConversionService

  @Mock
  lateinit var advancesMappingRepository: AdvancesMappingRepository

  @Spy
  lateinit var idempotencyService: GeneralLedgerIdempotencyService

  @Mock
  lateinit var accountResolver: GeneralLedgerAccountResolver

  @Spy
  lateinit var requestCache: InMemoryAccountCache

  private lateinit var advancesService: AdvancesService

  @BeforeEach
  fun setup() {
    requestCache.clear()
    advancesService = AdvancesService(
      advancesApiClient = advancesApiClient,
      timeConversionService = timeConversionService,
      advancesMappingRepository = advancesMappingRepository,
      idempotencyService = idempotencyService,
      accountResolver = accountResolver,
      requestCache = requestCache,
    )
  }

  @Nested
  inner class CreateAdvance {

    val syncAdvanceRequest = SyncCreateAdvanceRecordRequest(
      legacyPaymentProfileId = 123,
      legacyInformationNumber = "123456789",
      prisonNumber = "A123BX",
      amount = BigDecimal("123.00"),
      createdBy = "Test",
      comment = "",
      reference = "",
      prisonID = "LEI",
      createdOn = LocalDateTime.now(),
      repaymentStartDate = LocalDateTime.now(),
      repaymentAmount = BigDecimal("1.00"),
      status = CreateAdvanceRecordRequest.Status.ACTIVE,
      legacyTransactionId = 123,
    )

    val prisonSubAccount = UUID.randomUUID()
    val prisonerSubAccount = UUID.randomUUID()

    val requestCapture = argumentCaptor<CreateAdvanceRecordRequest>()

    @BeforeEach
    fun setup() {
      whenever(
        accountResolver.resolveSubAccount(
          prisonId = syncAdvanceRequest.prisonID,
          offenderId = "",
          accountCode = 1502,
          transactionType = "ADV",
          parentCache = requestCache,
        ),
      ).thenReturn(prisonSubAccount)

      whenever(
        accountResolver.resolveSubAccount(
          prisonId = "",
          offenderId = syncAdvanceRequest.prisonNumber,
          accountCode = 2102,
          transactionType = "ADV",
          parentCache = requestCache,
        ),
      ).thenReturn(prisonerSubAccount)

      val advanceRecordResponse = AdvanceRecordResponse(
        id = UUID.randomUUID(),
        legacyPaymentProfileId = syncAdvanceRequest.legacyPaymentProfileId,
        legacyInformationNumber = syncAdvanceRequest.legacyInformationNumber,
        prisonNumber = syncAdvanceRequest.prisonNumber,
        prisonID = syncAdvanceRequest.prisonID,
        amount = syncAdvanceRequest.amount.toPence(),
        createdOn = timeConversionService.toUtcInstant(syncAdvanceRequest.createdOn),
        repaymentStartDate = timeConversionService.toUtcInstant(syncAdvanceRequest.repaymentStartDate),
        repaymentAmount = syncAdvanceRequest.repaymentAmount.toPence(),
        createdBy = syncAdvanceRequest.createdBy,
        status = AdvanceRecordResponse.Status.valueOf(syncAdvanceRequest.status.name),
        reference = syncAdvanceRequest.reference,
        comment = syncAdvanceRequest.comment,
      )

      whenever(advancesApiClient.postAdvanceRecord(requestCapture.capture(), any()))
        .thenReturn(advanceRecordResponse)

      advancesService.createAdvance(syncAdvanceRequest)
    }

    @Test
    fun `Should call the advance service`() {
      verify(advancesApiClient, times(1))
        .postAdvanceRecord(any(), any())

      val capturedReq = requestCapture.firstValue

      assertThat(capturedReq.prisonNumber).isEqualTo(syncAdvanceRequest.prisonNumber)
      assertThat(capturedReq.createdBy).isEqualTo(syncAdvanceRequest.createdBy)
      assertThat(capturedReq.repaymentAmount).isEqualTo(syncAdvanceRequest.repaymentAmount.toPence())
      assertThat(capturedReq.reference).isEqualTo(syncAdvanceRequest.reference)
      assertThat(capturedReq.comment).isEqualTo(syncAdvanceRequest.comment)
      assertThat(capturedReq.legacyInformationNumber).isEqualTo(syncAdvanceRequest.legacyInformationNumber)
      assertThat(capturedReq.legacyTransactionId).isEqualTo(syncAdvanceRequest.legacyTransactionId)
      assertThat(capturedReq.amount).isEqualTo(syncAdvanceRequest.amount.toPence())
      assertThat(capturedReq.prisonID).isEqualTo(syncAdvanceRequest.prisonID)
      assertThat(capturedReq.status).isEqualTo(syncAdvanceRequest.status)
      assertThat(capturedReq.prisonSubAccountId).isEqualTo(prisonSubAccount)
      assertThat(capturedReq.prisonerSubAccountId).isEqualTo(prisonerSubAccount)
    }
  }
}
