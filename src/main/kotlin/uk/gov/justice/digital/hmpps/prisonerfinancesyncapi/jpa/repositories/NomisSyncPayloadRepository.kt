package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import java.time.Instant
import java.util.UUID

@Repository
interface NomisSyncPayloadRepository : JpaRepository<NomisSyncPayload, Long> {

  fun findByRequestId(requestId: UUID): NomisSyncPayload?
  fun findFirstByLegacyTransactionIdOrderByTimestampDesc(transactionId: Long): NomisSyncPayload?

  @Query(
    """
        SELECT n FROM NomisSyncPayload n 
        WHERE (:caseloadId IS NULL OR n.caseloadId = :caseloadId)
        AND (n.timestamp between :startDate AND :endDate)
    """,
  )
  fun findByCaseloadIdAndDateRange(caseloadId: String, startDate: Instant, endDate: Instant, pageable: Pageable): List<NomisSyncPayload>

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
