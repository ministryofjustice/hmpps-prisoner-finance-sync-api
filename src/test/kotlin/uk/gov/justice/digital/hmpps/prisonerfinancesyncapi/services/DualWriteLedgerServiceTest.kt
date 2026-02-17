package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DualWriteLedgerServiceTest {

  @Mock
  private lateinit var internalLedger: LedgerService

  @Mock
  private lateinit var generalLedger: LedgerService

  @Mock
  private lateinit var generalLedgerForwarder: GeneralLedgerForwarder

  private lateinit var dualWriteService: DualWriteLedgerService

  @BeforeEach
  fun setUp() {
    // InjectMocks messes up dependency injection because they have the same Interface, this is required here
    dualWriteService = DualWriteLedgerService(
      internalLedger,
      generalLedger,
      generalLedgerForwarder,
    )
  }

  val request = SyncOffenderTransactionRequest(
    transactionId = 485368707,
    requestId = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef"),
    caseloadId = "LEI",
    transactionTimestamp = LocalDateTime.now(),
    createdAt = LocalDateTime.now(),
    createdBy = "JD12345",
    createdByDisplayName = "J Doe",
    lastModifiedAt = null,
    lastModifiedBy = null,
    lastModifiedByDisplayName = null,
    listOf(
      OffenderTransaction(
        entrySequence = 2,
        offenderId = 5306470,
        offenderDisplayId = "AA001AA",
        offenderBookingId = 2970777,
        subAccountType = "CASG",
        postingType = "CR",
        type = "OR",
        description = "",
        amount = BigDecimal("5.99"),
        reference = null,
        generalLedgerEntries = emptyList(),
      ),
    ),
  )

  @Test
  fun `should return result from internalLedger and call General Ledger when syncing offender transaction`() {
    val lambdaCaptor = argumentCaptor<() -> List<UUID>>()
    val expectedResult = listOf(UUID.randomUUID())
    whenever(internalLedger.syncOffenderTransaction(request)).thenReturn(expectedResult)

    val res = dualWriteService.syncOffenderTransaction(request)

    verify(generalLedgerForwarder).executeIfEnabled(
      eq("Failed to sync Offender Transaction ${request.transactionId} to General Ledger"),
      eq(request.offenderTransactions.first().offenderDisplayId),
      lambdaCaptor.capture(),
    )

    verifyNoInteractions(generalLedger)

    lambdaCaptor.firstValue.invoke()
    verify(generalLedger).syncOffenderTransaction(request)

    verify(internalLedger).syncOffenderTransaction(request)
    assertThat(res).isEqualTo(expectedResult)
  }

  @Test
  fun `syncGeneralLedgerTransaction should only call internal Ledger`() {
    dualWriteService.syncGeneralLedgerTransaction(mock())
    verify(internalLedger).syncGeneralLedgerTransaction(any())
    verifyNoInteractions(generalLedger)
  }
}
