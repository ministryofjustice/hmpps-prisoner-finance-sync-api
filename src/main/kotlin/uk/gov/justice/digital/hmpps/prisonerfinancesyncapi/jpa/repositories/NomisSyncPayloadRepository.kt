package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import java.time.Instant
import java.util.UUID

@Repository
interface NomisSyncPayloadRepository :
  JpaRepository<NomisSyncPayload, Long>,
  JpaSpecificationExecutor<NomisSyncPayload> {

  fun findByRequestId(requestId: UUID): NomisSyncPayload?

  fun findFirstByLegacyTransactionIdOrderByTimestampDesc(transactionId: Long): NomisSyncPayload?

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

object NomisSyncPayloadSpecs {

  fun hasCaseloadId(prisonId: String?): Specification<NomisSyncPayload> = Specification { root, _, cb ->
    prisonId?.let { cb.equal(root.get<String>("caseloadId"), it) }
  }

  fun hasLegacyTransactionId(legacyId: Long?): Specification<NomisSyncPayload> = Specification { root, _, cb ->
    legacyId?.let { cb.equal(root.get<Long>("legacyTransactionId"), it) }
  }

  fun hasTransactionType(type: String?): Specification<NomisSyncPayload> = Specification { root, _, cb ->
    type?.let { cb.equal(root.get<String>("transactionType"), it) }
  }

  fun isAfterOrEqual(startDate: Instant?): Specification<NomisSyncPayload> = Specification { root, _, cb ->
    startDate?.let { cb.greaterThanOrEqualTo(root.get("timestamp"), it) }
  }

  fun isBefore(endDate: Instant?): Specification<NomisSyncPayload> = Specification { root, _, cb ->
    endDate?.let { cb.lessThan(root.get("timestamp"), it) }
  }

  fun applyCursor(cursorTimestamp: Instant?, cursorId: Long?): Specification<NomisSyncPayload> = Specification { root, _, cb ->
    if (cursorTimestamp == null || cursorId == null) return@Specification null

    val beforeCursor = cb.lessThan(root.get("timestamp"), cursorTimestamp)
    val sameTimestampSmallerId = cb.and(
      cb.equal(root.get<Instant>("timestamp"), cursorTimestamp),
      cb.lessThan(root.get("id"), cursorId),
    )

    cb.or(beforeCursor, sameTimestampSmallerId)
  }
}
