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
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AdvanceRepaymentMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AdvanceRepaymentMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AdvancesMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncAdvanceRepayResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRepayRequest
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

  @Mock
  lateinit var advanceRepaymentMappingRepository: AdvanceRepaymentMappingRepository

  @Spy
  lateinit var idempotencyService: GeneralLedgerIdempotencyService

  private lateinit var advancesService: AdvancesService

  @BeforeEach
  fun setup() {
    advancesService = AdvancesService(
      advancesApiClient = advancesApiClient,
      timeConversionService = timeConversionService,
      advancesMappingRepository = advancesMappingRepository,
      idempotencyService = idempotencyService,
      advanceRepaymentMappingRepository = advanceRepaymentMappingRepository,
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
        updatedAt = null,
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
    }
  }

  @Nested
  inner class CreateAdvanceRepayment {

    val advanceId = UUID.randomUUID()
    val capturedAdvanceRepaymentRequest = argumentCaptor<CreateAdvanceRepaymentRequest>()
    val capturedIdempotencyKey = argumentCaptor<UUID>()
    val capturedAdvanceId = argumentCaptor<UUID>()
    val capturedAdvanceRepaymentMapping = argumentCaptor<AdvanceRepaymentMapping>()

    val syncRepaymentRequest = SyncCreateAdvanceRepayRequest(
      amount = BigDecimal("0.50"),
      transactionId = 123456,
      createdAt = LocalDateTime.now(),
      createdBy = "Test",
      description = "Test Description",
      entrySequence = 2,
      caseloadId = "LEI",
    )

    val timeConversionService = TimeConversionService()

    val glUIID = UUID.randomUUID()

    lateinit var repaymentResponse: SyncAdvanceRepayResponse

    @BeforeEach
    fun setup() {
      whenever(
        advancesApiClient.postAdvanceRepayment(
          any(),
          any(),
          any(),
        ),
      )
        .thenReturn(
          AdvanceRepaymentResponse(
            id = UUID.randomUUID(),
            advanceId = advanceId,
            amount = syncRepaymentRequest.amount.toPence(),
            createdAt = timeConversionService.toUtcInstant(syncRepaymentRequest.createdAt),
            createdBy = syncRepaymentRequest.createdBy,
            transactionId = glUIID,
            description = syncRepaymentRequest.description,
            legacyTransactionId = syncRepaymentRequest.transactionId,
          ),
        )

      repaymentResponse = advancesService.createAdvanceRepayment(advanceId, syncRepaymentRequest)
    }

    @Test
    fun `should send the advance repayment request`() {
      verify(advancesApiClient, times(1))
        .postAdvanceRepayment(
          advanceId = capturedAdvanceId.capture(),
          idempotencyKey = capturedIdempotencyKey.capture(),
          request = capturedAdvanceRepaymentRequest.capture(),
        )

      val capturedReq = capturedAdvanceRepaymentRequest.firstValue
      val usedIdempotencyKey = capturedIdempotencyKey.firstValue
      val capturedAdvanceId = capturedAdvanceId.firstValue

      assertThat(capturedAdvanceId).isEqualTo(advanceId)
      assertThat(capturedReq.legacyTransactionId).isEqualTo(syncRepaymentRequest.transactionId)
      assertThat(capturedReq.amount).isEqualTo(syncRepaymentRequest.amount.toPence())
      assertThat(capturedReq.description).isEqualTo(syncRepaymentRequest.description)
      assertThat(capturedReq.createdBy).isEqualTo(syncRepaymentRequest.createdBy)

      val expectedIdempotencyKey = idempotencyService.genTransactionIdempotencyKey(
        syncRepaymentRequest.transactionId,
        entrySequence = syncRepaymentRequest.entrySequence,
      )
      assertThat(expectedIdempotencyKey).isEqualTo(usedIdempotencyKey)

      assertThat(repaymentResponse.repaymentTransactionId).isEqualTo(glUIID)
    }

    @Test
    fun `should save to advance repayment mapping table`() {
      verify(advanceRepaymentMappingRepository, times(1))
        .saveAndFlush(capturedAdvanceRepaymentMapping.capture())

      val savedMapping = capturedAdvanceRepaymentMapping.firstValue

      assertThat(savedMapping.generalLedgerTransactionUuid).isEqualTo(glUIID)
      assertThat(savedMapping.entrySequence).isEqualTo(syncRepaymentRequest.entrySequence)
      assertThat(savedMapping.legacyTransactionId).isEqualTo(syncRepaymentRequest.transactionId)
    }
  }
}
