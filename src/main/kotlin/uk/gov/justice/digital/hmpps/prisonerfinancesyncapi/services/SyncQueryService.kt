package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.NomisSyncPayloadRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionResponse
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

@Service
class SyncQueryService(
  private val nomisSyncPayloadRepository: NomisSyncPayloadRepository,
  private val responseMapperService: ResponseMapperService,
  private val timeConversionService: TimeConversionService,
) {

  fun findByRequestId(requestId: UUID): NomisSyncPayload? = nomisSyncPayloadRepository.findByRequestId(requestId)

  fun findByLegacyTransactionId(transactionId: Long): NomisSyncPayload? = nomisSyncPayloadRepository.findFirstByLegacyTransactionIdOrderByTimestampDesc(transactionId)

  fun findNomisSyncPayloadBySynchronizedTransactionId(synchronizedTransactionId: UUID): NomisSyncPayload? = nomisSyncPayloadRepository.findFirstBySynchronizedTransactionIdOrderByTimestampDesc(synchronizedTransactionId)

  fun getGeneralLedgerTransactionsByDate(
    startDate: LocalDate,
    endDate: LocalDate,
    page: Int,
    size: Int,
  ): Page<SyncGeneralLedgerTransactionResponse> {
    val pageable = PageRequest.of(page, size)
    val nomisPayloadsPage = findNomisSyncPayloadsByTimestampAndType(startDate, endDate, SyncGeneralLedgerTransactionRequest::class, pageable)
    return nomisPayloadsPage.map { payload ->
      responseMapperService.mapToGeneralLedgerTransactionResponse(payload)
    }
  }

  fun getOffenderTransactionsByDate(
    startDate: LocalDate,
    endDate: LocalDate,
    page: Int,
    size: Int,
  ): Page<SyncOffenderTransactionResponse> {
    val pageable = PageRequest.of(page, size)
    val nomisPayloadsPage = findNomisSyncPayloadsByTimestampAndType(startDate, endDate, SyncOffenderTransactionRequest::class, pageable)
    return nomisPayloadsPage.map { payload ->
      responseMapperService.mapToOffenderTransactionResponse(payload)
    }
  }

  fun getGeneralLedgerTransactionById(id: UUID): SyncGeneralLedgerTransactionResponse? {
    val payload = findNomisSyncPayloadBySynchronizedTransactionId(id)
    return if (payload != null && payload.requestTypeIdentifier == SyncGeneralLedgerTransactionRequest::class.simpleName) {
      responseMapperService.mapToGeneralLedgerTransactionResponse(payload)
    } else {
      null
    }
  }

  fun getOffenderTransactionById(id: UUID): SyncOffenderTransactionResponse? {
    val payload = findNomisSyncPayloadBySynchronizedTransactionId(id)
    return if (payload != null && payload.requestTypeIdentifier == SyncOffenderTransactionRequest::class.simpleName) {
      responseMapperService.mapToOffenderTransactionResponse(payload)
    } else {
      null
    }
  }

  private fun findNomisSyncPayloadsByTimestampAndType(
    startDate: LocalDate,
    endDate: LocalDate,
    requestType: KClass<*>,
    pageable: Pageable,
  ): Page<NomisSyncPayload> {
    val startInstant = timeConversionService.toUtcStartOfDay(startDate)
    val endInstant = timeConversionService.toUtcStartOfDay(endDate.plusDays(1))

    return nomisSyncPayloadRepository.findLatestByTransactionTimestampBetweenAndRequestTypeIdentifier(
      startInstant,
      endInstant,
      requestType.simpleName!!,
      pageable,
    )
  }
}
