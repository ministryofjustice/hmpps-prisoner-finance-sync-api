package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.AdvancesApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AdvanceMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AdvanceRepaymentMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AdvanceRepaymentMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.AdvancesMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRecordResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceRepaymentResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.AdvanceWriteOffRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.CreateAdvanceRepaymentRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncAdvanceRepayResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceRepayRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncCreateAdvanceWriteOffRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.advances.SyncMigrateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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

  @Mock
  lateinit var accountResolver: GeneralLedgerAccountResolver

  private lateinit var advancesService: AdvancesService

  @BeforeEach
  fun setup() {
    advancesService = AdvancesService(
      advancesApiClient = advancesApiClient,
      timeConversionService = timeConversionService,
      advancesMappingRepository = advancesMappingRepository,
      idempotencyService = idempotencyService,
      advanceRepaymentMappingRepository = advanceRepaymentMappingRepository,
      accountResolver = accountResolver,
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
            entrySequence = 2,
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
      assertThat(capturedReq.entrySequence).isEqualTo(syncRepaymentRequest.entrySequence)

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
        .save(capturedAdvanceRepaymentMapping.capture())

      val savedMapping = capturedAdvanceRepaymentMapping.firstValue

      assertThat(savedMapping.generalLedgerTransactionUuid).isEqualTo(glUIID)
      assertThat(savedMapping.entrySequence).isEqualTo(syncRepaymentRequest.entrySequence)
      assertThat(savedMapping.legacyTransactionId).isEqualTo(syncRepaymentRequest.transactionId)
    }

    @Test
    fun `should not throw an error if the mapping already exists when saving`() {
      whenever { advanceRepaymentMappingRepository.save(any<AdvanceRepaymentMapping>()) }
        .thenThrow(DataIntegrityViolationException("duplicate key value violates unique constraint \"uc_legacy_transaction_id_and_entry_sequence\""))

      val actualResponse = advancesService.createAdvanceRepayment(advanceId, syncRepaymentRequest)

      assertThat(actualResponse.repaymentTransactionId).isEqualTo(glUIID)
    }
  }

  @Nested
  inner class WriteOffAdvance {
    @Test
    fun `should call the advance service`() {
      val advanceId = UUID.randomUUID()
      val requestCaptor = argumentCaptor<AdvanceWriteOffRequest>()

      whenever(
        advancesApiClient.postWriteOffAdvance(
          advanceId = eq(advanceId),
          request = requestCaptor.capture(),
        ),
      ).thenReturn(
        AdvanceRecordResponse(
          id = advanceId,
          legacyPaymentProfileId = 123,
          legacyInformationNumber = "23113",
          prisonNumber = "A1234AB",
          prisonID = "LEI",
          amount = 123,
          createdOn = Instant.now(),
          repaymentStartDate = Instant.now(),
          repaymentAmount = 1,
          createdBy = "TEST",
          status = AdvanceRecordResponse.Status.WRITTEN_OFF,
          reference = "",
          comment = "",
          updatedAt = null,
          generalLedgerUUID = UUID.randomUUID(),
        ),
      )

      val syncAdvanceWriteOffRequest = SyncCreateAdvanceWriteOffRequest(
        writeOffDateTime = LocalDateTime.now(),
        writtenOffBy = "TEST",
        writtenOffAmount = BigDecimal("10.10"),
      )

      val response = advancesService.writeOffAdvance(advanceId, syncAdvanceWriteOffRequest)

      assertThat(response.advanceId).isEqualTo(advanceId)
      assertThat(response.advanceStatus).isEqualTo(AdvanceRecordResponse.Status.WRITTEN_OFF)

      val request = requestCaptor.firstValue

      assertThat(request.writtenOffAmount).isEqualTo(syncAdvanceWriteOffRequest.writtenOffAmount.toPence())
      assertThat(request.writtenOffBy).isEqualTo(syncAdvanceWriteOffRequest.writtenOffBy)
      assertThat(request.writeOffDateTime.truncatedTo(ChronoUnit.MILLIS)).isEqualTo(
        timeConversionService.toUtcInstant(syncAdvanceWriteOffRequest.writeOffDateTime)
          .truncatedTo(ChronoUnit.MILLIS),
      )

      verify(advancesApiClient, times(1))
        .postWriteOffAdvance(eq(advanceId), any<AdvanceWriteOffRequest>())
    }
  }

  @Nested
  inner class MigrateAdvance {

    val advanceId = UUID.randomUUID()
    val legacyPaymentProfileId = 1234L
    val legacyInformationNumber = "23113-1"
    val prisonId = "LEI"
    val prisonNumber = "A1234AB"

    val migrateRequest = SyncMigrateAdvanceRecordRequest(
      legacyPaymentProfileId = legacyPaymentProfileId,
      legacyInformationNumber = legacyInformationNumber,
      prisonNumber = prisonNumber,
      prisonID = prisonId,
      amountIssued = BigDecimal("10.0"),
      amountOutstanding = BigDecimal("10.0"),
      createdOn = LocalDateTime.now(),
      balanceCalculationTime = LocalDateTime.now(),
      repaymentStartDate = LocalDateTime.now().plusDays(1),
      repaymentAmount = BigDecimal("2.00"),
      reference = "",
      comment = "",
      createdBy = "TEST",
      status = CreateAdvanceRecordRequest.Status.ACTIVE,
      legacyTransactionId = 1234567,
    )

    val advanceMigrationRequest = migrateRequest.toAdvanceMigrationRequest()

    val timeConversionService = TimeConversionService()

    val advanceRecordResponse = AdvanceRecordResponse(
      id = advanceId,
      legacyPaymentProfileId = legacyPaymentProfileId,
      legacyInformationNumber = legacyInformationNumber,
      prisonNumber = prisonNumber,
      prisonID = prisonId,
      amount = migrateRequest.amountIssued.toPence(),
      createdOn = timeConversionService.toUtcInstant(migrateRequest.createdOn),
      repaymentStartDate = timeConversionService.toUtcInstant(migrateRequest.repaymentStartDate),
      repaymentAmount = migrateRequest.repaymentAmount.toPence(),
      createdBy = migrateRequest.createdBy,
      status = AdvanceRecordResponse.Status.ACTIVE,
      reference = migrateRequest.reference,
      comment = migrateRequest.comment,
      updatedAt = null,
      generalLedgerUUID = null,
    )

    val prisonSubAccountRefAdvances = 1502
    val prisonerSpendsRef = 2102

    @Test
    fun `Should call the migrateAdvance endpoint and return advance record`() {
      val captor = argumentCaptor<AdvanceMigrationRequest>()
      whenever(
        advancesApiClient.postMigrateAdvance(captor.capture()),
      ).thenReturn(advanceRecordResponse)

      whenever { advancesMappingRepository.save(any<AdvanceMapping>()) }.thenAnswer { it.arguments[0] }

      val response = advancesService.migrateAdvance(migrateRequest)

      verify(advancesApiClient, times(1)).postMigrateAdvance(any())

      verify(accountResolver, times(1)).resolvePrisonSubAccount(
        prisonId = eq(prisonId),
        accountCode = eq(prisonSubAccountRefAdvances),
        transactionType = eq("ADV"),
        parentCache = any<InMemoryAccountCache>(),
      )

      verify(accountResolver, times(1)).resolvePrisonerSubAccount(
        offenderId = eq(prisonNumber),
        accountCode = eq(prisonerSpendsRef),
        parentCache = any<InMemoryAccountCache>(),
      )

      assertThat(response.advanceId).isEqualTo(advanceId)
      assertThat(response.paymentProfileId).isEqualTo(legacyPaymentProfileId)
      assertThat(captor.firstValue).isEqualTo(advanceMigrationRequest)
    }

    @Test
    fun `Should save advance mapping on migrate`() {
      whenever(
        advancesApiClient.postMigrateAdvance(any()),
      ).thenReturn(advanceRecordResponse)

      whenever { advancesMappingRepository.save(any<AdvanceMapping>()) }.thenAnswer { it.arguments[0] }

      advancesService.migrateAdvance(migrateRequest)

      val capturedAdvanceMapping = argumentCaptor<AdvanceMapping>()

      verify(advancesMappingRepository, times(1))
        .save(capturedAdvanceMapping.capture())

      val savedMapping = capturedAdvanceMapping.firstValue

      assertThat(savedMapping.legacyPaymentProfileId).isEqualTo(legacyPaymentProfileId)
      assertThat(savedMapping.advanceUuid).isEqualTo(advanceId)
    }

    @ParameterizedTest(name = "should not throw error when unique constraint violated on {0}")
    @ValueSource(
      strings = [
        "uc_advance_mapping_advance_uuid",
        "uc_advance_mapping_legacy_payment_profile_id",
      ],
    )
    fun `should not throw an error if the mapping already exists when saving`(constraintName: String) {
      whenever(
        advancesApiClient.postMigrateAdvance(any()),
      ).thenReturn(advanceRecordResponse)

      whenever { advancesMappingRepository.save(any<AdvanceMapping>()) }
        .thenThrow(DataIntegrityViolationException("duplicate key value violates unique constraint \"$constraintName\""))

      val response = advancesService.migrateAdvance(migrateRequest)

      verify(advancesApiClient, times(1)).postMigrateAdvance(any())

      assertThat(response.advanceId).isEqualTo(advanceId)
      assertThat(response.paymentProfileId).isEqualTo(legacyPaymentProfileId)
    }
  }
}
