package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.SubscriptionsApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.SubscriptionsMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.SubscriptionsMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.CreateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SubscriptionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.subscriptions.SyncCreateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toPence
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SubscriptionsServiceTest {

  val timeConversionService = TimeConversionService()
  val generalLedgerIdempotencyService = GeneralLedgerIdempotencyService()

  @Mock
  lateinit var subscriptionsApiClient: SubscriptionsApiClient

  @Mock
  lateinit var subscriptionsMappingRepository: SubscriptionsMappingRepository

  private val ledgerAccountMappingService = LedgerAccountMappingService()

  private lateinit var subscriptionsService: SubscriptionsService

  @BeforeEach
  fun setup() {
    subscriptionsService = SubscriptionsService(
      subscriptionsApiClient = subscriptionsApiClient,
      generalLedgerIdempotencyService = generalLedgerIdempotencyService,
      timeConversionService = timeConversionService,
      ledgerAccountMappingService = ledgerAccountMappingService,
      subscriptionsMappingRepository = subscriptionsMappingRepository,
    )
  }

  @Nested
  inner class CreateSubscription {

    val legacyPaymentProfileId = 123L
    val entrySequence = 1
    val expectedIdempotencyKey = generalLedgerIdempotencyService.genTransactionIdempotencyKey(legacyPaymentProfileId, entrySequence)

    val syncSubscriptionRequest = SyncCreateSubscriptionRequest(
      legacyPaymentProfileId = legacyPaymentProfileId,
      amount = BigDecimal("123.00"),
      dayOfPayment = DayOfWeek.WEDNESDAY,
      startDate = LocalDate.now(),
      endDate = LocalDate.now().plusDays(7),
      subscriptionType = "TELE",
      reference = "REF",
      createdBy = "USER_JOHN",
      createdAt = LocalDateTime.now(),
      prisonId = "LEI",
      prisonNumber = "A1234AA",
      subAccountCode = 2101,
    )

    val requestCaptor = argumentCaptor<CreateSubscriptionRequest>()
    val idempotencyKeyCaptor = argumentCaptor<UUID>()
    val subscriptionMappingCaptor = argumentCaptor<SubscriptionsMapping>()

    val subscriptionRecordResponse = SubscriptionResponse(
      id = UUID.randomUUID(),
      legacyPaymentProfileId = syncSubscriptionRequest.legacyPaymentProfileId,
      amount = syncSubscriptionRequest.amount.toPence(),
      dayOfPayment = SubscriptionResponse.DayOfPayment.WEDNESDAY,
      startDate = syncSubscriptionRequest.startDate,
      subscriptionType = syncSubscriptionRequest.subscriptionType,
      createdBy = syncSubscriptionRequest.createdBy,
      createdAt = timeConversionService.toUtcInstant(syncSubscriptionRequest.createdAt),
      prisonId = syncSubscriptionRequest.prisonId,
      prisonNumber = syncSubscriptionRequest.prisonNumber,
      subAccountRef = SubscriptionResponse.SubAccountRef.CASH,
      endDate = syncSubscriptionRequest.endDate,
      reference = syncSubscriptionRequest.reference,
    )

    @BeforeEach
    fun setup() {
      whenever(subscriptionsApiClient.postSubscription(requestCaptor.capture(), idempotencyKeyCaptor.capture()))
        .thenReturn(subscriptionRecordResponse)

      whenever(subscriptionsMappingRepository.save(subscriptionMappingCaptor.capture())).thenAnswer { it.arguments[0] }

      subscriptionsService.createSubscription(syncSubscriptionRequest)
    }

    @Test
    fun `should call the subscriptions service`() {
      verify(subscriptionsApiClient, times(1))
        .postSubscription(any(), any())

      val capturedIdempotencyKey = idempotencyKeyCaptor.firstValue
      assertThat(capturedIdempotencyKey).isEqualTo(expectedIdempotencyKey)

      val capturedRequest = requestCaptor.firstValue
      assertThat(capturedRequest.legacyPaymentProfileId).isEqualTo(syncSubscriptionRequest.legacyPaymentProfileId)
      assertThat(capturedRequest.amount).isEqualTo(syncSubscriptionRequest.amount.toPence())
      assertThat(capturedRequest.dayOfPayment).isEqualTo(CreateSubscriptionRequest.DayOfPayment.WEDNESDAY)
      assertThat(capturedRequest.startDate).isEqualTo(syncSubscriptionRequest.startDate)
      assertThat(capturedRequest.subscriptionType).isEqualTo(syncSubscriptionRequest.subscriptionType)
      assertThat(capturedRequest.createdBy).isEqualTo(syncSubscriptionRequest.createdBy)
      assertThat(capturedRequest.createdAt).isEqualTo(timeConversionService.toUtcInstant(syncSubscriptionRequest.createdAt))
      assertThat(capturedRequest.prisonId).isEqualTo(syncSubscriptionRequest.prisonId)
      assertThat(capturedRequest.prisonNumber).isEqualTo(syncSubscriptionRequest.prisonNumber)
      assertThat(capturedRequest.subAccountRef).isEqualTo(CreateSubscriptionRequest.SubAccountRef.CASH)
      assertThat(capturedRequest.endDate).isEqualTo(syncSubscriptionRequest.endDate)
      assertThat(capturedRequest.reference).isEqualTo(syncSubscriptionRequest.reference)
    }

    @Test
    fun `should save a mapping between the legacy payment profile id and the subscription id`() {
      verify(subscriptionsMappingRepository, times(1))
        .save(any())

      val capturedSubscriptionMapping = subscriptionMappingCaptor.firstValue

      assertThat(capturedSubscriptionMapping.legacyPaymentProfileId).isEqualTo(syncSubscriptionRequest.legacyPaymentProfileId)
      assertThat(capturedSubscriptionMapping.subscriptionUuid).isEqualTo(subscriptionRecordResponse.id)
    }

    @ParameterizedTest
    @CsvSource("uc_subscriptions_mapping_legacy_payment_profile_id", "uc_subscriptions_mapping_subscription_uuid")
    fun `should still return a response if the mapping already exists`(constraint: String) {
      whenever(subscriptionsMappingRepository.save(any<SubscriptionsMapping>()))
        .thenThrow(DataIntegrityViolationException("duplicate key value violates unique constraint \"$constraint\""))

      verify(subscriptionsMappingRepository, times(1))
        .save(any())

      val response = subscriptionsService.createSubscription(syncSubscriptionRequest)

      assertThat(response.subscriptionId).isEqualTo(subscriptionRecordResponse.id)
    }
  }
}
