package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionResponse

class ResponseMapperServiceTest {

  private lateinit var objectMapper: ObjectMapper
  private lateinit var dummySyncGeneralLedgerTransactionRequest: SyncGeneralLedgerTransactionRequest
  private lateinit var dummySyncOffenderTransactionRequest: SyncOffenderTransactionRequest

  @Mock
  private lateinit var responseMapperService: ResponseMapperService

  @BeforeEach
  fun setUp() {

    objectMapper = ObjectMapper()
      .registerModule(JavaTimeModule())
      .registerModule(KotlinModule.Builder().build())
    responseMapperService = ResponseMapperService()

    dummySyncGeneralLedgerTransactionRequest = SyncGeneralLedgerTransactionRequest(
      transactionId = 1000001,
      requestId = UUID.fromString("c3d4e5f6-a7b8-9012-3456-7890abcdef01"),
      description = "General Ledger Account Transfer",
      reference = "REF12345",
      caseloadId = "MDI",
      transactionType = "GJ",
      transactionTimestamp = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      createdBy = "JD12346",
      createdByDisplayName = "J. Smith",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      generalLedgerEntries = listOf(),
    )

    dummySyncOffenderTransactionRequest = SyncOffenderTransactionRequest(
      transactionId = 1000002,
      requestId = UUID.fromString("c3d4e5f6-a7b8-9012-3456-7890abcdef01"),
      caseloadId = "MDI",
      transactionTimestamp = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      createdBy = "JD12346",
      createdByDisplayName = "J. Smith",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      offenderTransactions = listOf(),
    )
  }

  @Test
  fun `given SyncGeneralLedgerTransactionResponse an empty body when deserializing should throw MismatchedInputException`(){

    val nomisPayload = NomisSyncPayload(
      timestamp = Instant.now(),
      legacyTransactionId = 1002,
      requestId = UUID.randomUUID(),
      caseloadId = "MDI",
      requestTypeIdentifier = SyncGeneralLedgerTransactionRequest::class.simpleName,
      synchronizedTransactionId = UUID.randomUUID(),
      body = "",
      transactionTimestamp = Instant.now(),
    )
    val ex = assertThrows(MismatchedInputException::class.java) {
      responseMapperService.mapToGeneralLedgerTransactionResponse(nomisPayload)
    }
    assert(ex.message!!.contains("No content to map"))
  }

  @Test
  fun `should return true when NomisSyncPayload maps to SyncGeneralLedgerTransactionResponse`() {

    val bodySyncGeneralLedgerTransaction = objectMapper.writeValueAsString(dummySyncGeneralLedgerTransactionRequest)

    val nomisPayload = NomisSyncPayload(
      timestamp = Instant.now(),
      legacyTransactionId = 1001,
      requestId = UUID.randomUUID(),
      caseloadId = "MDI",
      requestTypeIdentifier = SyncGeneralLedgerTransactionRequest::class.simpleName,
      synchronizedTransactionId = UUID.randomUUID(),
      body = bodySyncGeneralLedgerTransaction,
      transactionTimestamp = Instant.now(),
    )

    val mapperResponse = responseMapperService.mapToGeneralLedgerTransactionResponse(nomisPayload)

    assertThat(mapperResponse).isNotNull
    assertThat(mapperResponse.synchronizedTransactionId).isEqualTo(nomisPayload.synchronizedTransactionId)
    assertThat(mapperResponse.legacyTransactionId).isEqualTo(nomisPayload.legacyTransactionId)
    assertThat(mapperResponse.reference).isEqualTo(dummySyncGeneralLedgerTransactionRequest.reference)
    assertThat(mapperResponse.caseloadId).isEqualTo(dummySyncGeneralLedgerTransactionRequest.caseloadId)
    assertThat(mapperResponse.transactionType).isEqualTo(dummySyncGeneralLedgerTransactionRequest.transactionType)
    assertThat(mapperResponse.transactionTimestamp).isEqualTo(dummySyncGeneralLedgerTransactionRequest.transactionTimestamp)
    assertThat(mapperResponse.createdAt).isEqualTo(dummySyncGeneralLedgerTransactionRequest.createdAt)
    assertThat(mapperResponse.createdBy).isEqualTo(dummySyncGeneralLedgerTransactionRequest.createdBy)
    assertThat(mapperResponse.createdByDisplayName).isEqualTo(dummySyncGeneralLedgerTransactionRequest.createdByDisplayName)
    assertThat(mapperResponse.lastModifiedAt).isEqualTo(dummySyncGeneralLedgerTransactionRequest.lastModifiedAt)
    assertThat(mapperResponse.lastModifiedBy).isEqualTo(dummySyncGeneralLedgerTransactionRequest.lastModifiedBy)
    assertThat(mapperResponse.lastModifiedByDisplayName).isEqualTo(dummySyncGeneralLedgerTransactionRequest.lastModifiedByDisplayName)
  }

  @Test
  fun `given GeneralLedgerTransactionResponse an empty body when deserializing should throw MismatchedInputException`(){

    val nomisPayload = NomisSyncPayload(
      timestamp = Instant.now(),
      legacyTransactionId = 1002,
      requestId = UUID.randomUUID(),
      caseloadId = "MDI",
      requestTypeIdentifier = SyncOffenderTransactionResponse::class.simpleName,
      synchronizedTransactionId = UUID.randomUUID(),
      body = "",
      transactionTimestamp = Instant.now(),
    )
    val ex = assertThrows(MismatchedInputException::class.java) {
      responseMapperService.mapToGeneralLedgerTransactionResponse(nomisPayload)
    }
    assert(ex.message!!.contains("No content to map"))
  }


@Test
fun `should return true when NomisSyncPayload maps to OffenderTransactionResponse`() {

  val bodySyncOffenderTransactionRequest= objectMapper.writeValueAsString(dummySyncOffenderTransactionRequest)

  val nomisPayload = NomisSyncPayload(
    timestamp = Instant.now(),
    legacyTransactionId = 1002,
    requestId = UUID.randomUUID(),
    caseloadId = "MDI",
    requestTypeIdentifier = SyncOffenderTransactionResponse::class.simpleName,
    synchronizedTransactionId = UUID.randomUUID(),
    body = bodySyncOffenderTransactionRequest,
    transactionTimestamp = Instant.now(),
  )

  val mapperResponse = responseMapperService.mapToOffenderTransactionResponse(nomisPayload)

  assertThat(mapperResponse).isNotNull
  assertThat(mapperResponse.synchronizedTransactionId).isEqualTo(nomisPayload.synchronizedTransactionId)
  assertThat(mapperResponse.legacyTransactionId).isEqualTo(nomisPayload.legacyTransactionId)
  assertThat(mapperResponse.synchronizedTransactionId).isEqualTo(nomisPayload.synchronizedTransactionId)
  assertThat(mapperResponse.legacyTransactionId).isEqualTo(nomisPayload.legacyTransactionId)
  assertThat(mapperResponse.caseloadId).isEqualTo(dummySyncGeneralLedgerTransactionRequest.caseloadId)
  assertThat(mapperResponse.transactionTimestamp).isEqualTo(dummySyncGeneralLedgerTransactionRequest.transactionTimestamp)
  assertThat(mapperResponse.createdAt).isEqualTo(dummySyncGeneralLedgerTransactionRequest.createdAt)
  assertThat(mapperResponse.createdBy).isEqualTo(dummySyncGeneralLedgerTransactionRequest.createdBy)
  assertThat(mapperResponse.createdByDisplayName).isEqualTo(dummySyncGeneralLedgerTransactionRequest.createdByDisplayName)
  assertThat(mapperResponse.lastModifiedAt).isEqualTo(dummySyncGeneralLedgerTransactionRequest.lastModifiedAt)
  assertThat(mapperResponse.lastModifiedBy).isEqualTo(dummySyncGeneralLedgerTransactionRequest.lastModifiedBy)
  assertThat(mapperResponse.lastModifiedByDisplayName).isEqualTo(dummySyncGeneralLedgerTransactionRequest.lastModifiedByDisplayName)

  }
}