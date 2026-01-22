package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadSummary
import java.time.Instant
import java.util.UUID

@Repository
interface NomisSyncPayloadRepository : JpaRepository<NomisSyncPayload, Long> {

  fun findByRequestId(requestId: UUID): NomisSyncPayload?
  fun findFirstByLegacyTransactionIdOrderByTimestampDesc(transactionId: Long): NomisSyncPayload?

  @Query(
    """
        SELECT 
          n.legacyTransactionId as legacyTransactionId,
          n.synchronizedTransactionId as synchronizedTransactionId,
          n.caseloadId as caseloadId,
          n.timestamp as timestamp,
          n.requestTypeIdentifier as requestTypeIdentifier,
          n.requestId as requestId,
          n.transactionTimestamp as transactionTimestamp
        FROM NomisSyncPayload n 
        WHERE (:prisonId IS NULL OR n.caseloadId = :prisonId) AND
              (:startDate IS NULL OR n.timestamp >= :startDate ) AND
              (:endDate IS NULL OR n.timestamp >= :endDate ) 
    """,
  )
  fun findByCaseloadIdAndDateRange(prisonId: String?, startDate: Instant?, endDate: Instant?, pageable: Pageable): Page<NomisSyncPayloadSummary>

  @Query(
    """
        SELECT p
        FROM NomisSyncPayload p
        WHERE p.transactionTimestamp BETWEEN :startDate AND :endDate
        AND p.requestTypeIdentifier = :requestTypeIdentifier
        AND p.timestamp = (
            SELECT MAX(p2.timestamp)
            FROM NomisSyncPayload p2
            WHERE p2.synchronizedTransactionId = p.synchronizedTransactionId
        )
    """,
  )
  fun findLatestByTransactionTimestampBetweenAndRequestTypeIdentifier(
    @Param("startDate") startDate: Instant,
    @Param("endDate") endDate: Instant,
    @Param("requestTypeIdentifier") requestTypeIdentifier: String,
    pageable: Pageable,
  ): Page<NomisSyncPayload>

  fun findFirstBySynchronizedTransactionIdOrderByTimestampDesc(synchronizedTransactionId: UUID): NomisSyncPayload?
}
