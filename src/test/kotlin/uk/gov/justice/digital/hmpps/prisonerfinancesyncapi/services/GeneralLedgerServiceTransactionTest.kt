package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlPostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlSubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils.toGLLong
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

@ExtendWith(MockitoExtension::class)
class GeneralLedgerServiceTransactionTest {

  @Mock
  private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @InjectMocks
  private lateinit var generalLedgerService: GeneralLedgerService

  @Spy
  private lateinit var timeConversionService: TimeConversionService

  @Spy
  private lateinit var accountMapping: LedgerAccountMappingService

  private val offenderDisplayId = "A1234AA"

  fun mockAccount(reference: String, accountUUID: UUID = UUID.randomUUID()) {
    val mockGLAccountResponse = mock<GlAccountResponse>()
    whenever(generalLedgerApiClient.findAccountByReference(reference))
      .thenReturn(mockGLAccountResponse)

    whenever(mockGLAccountResponse.id).thenReturn(accountUUID)
  }

  fun mockSubAccount(parentReference: String, subAccountReference: String, accountUUID: UUID = UUID.randomUUID()) {
    val mockGLSubAccountResponse = mock<GlSubAccountResponse>()
    whenever(generalLedgerApiClient.findSubAccount(parentReference, subAccountReference))
      .thenReturn(mockGLSubAccountResponse)

    whenever(mockGLSubAccountResponse.id).thenReturn(accountUUID)
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

    generalLedgerService.syncOffenderTransaction(request)

    val glTransactionRequest = GlTransactionRequest(
      reference = request.offenderTransactions[0].reference!!,
      description = request.offenderTransactions[0].description,
      timestamp = request.transactionTimestamp.toInstant(ZoneOffset.UTC),
      amount = amount.toGLLong(),
      postings = listOf(
        GlPostingRequest(subAccountUUIDPrison, PostingType.CR, amount.toGLLong()),
        GlPostingRequest(subAccountUUIDPrisoner, PostingType.DR, amount.toGLLong()),
      ),
    )

    verify(generalLedgerApiClient).postTransaction(glTransactionRequest)
  }
}
