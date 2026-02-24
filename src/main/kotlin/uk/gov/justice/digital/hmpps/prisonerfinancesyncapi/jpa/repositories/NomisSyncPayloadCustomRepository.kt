package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.NomisSyncPayload
import java.time.Instant

@Repository
class NomisSyncPayloadCustomRepository(
  @PersistenceContext private val em: EntityManager,
) {

  fun findMatchingPayloads(
    prisonId: String?,
    legacyTransactionId: Long?,
    transactionType: String?,
    startDate: Instant?,
    endDate: Instant?,
    cursorTimestamp: Instant?,
    cursorId: Long?,
    pageable: Pageable,
  ): List<NomisSyncPayload> {
    val cb = em.criteriaBuilder
    val query = cb.createQuery(NomisSyncPayload::class.java)
    val root = query.from(NomisSyncPayload::class.java)

    val predicates = mutableListOf<Predicate>()

    prisonId?.let { predicates.add(cb.equal(root.get<String>("caseloadId"), it)) }
    legacyTransactionId?.let { predicates.add(cb.equal(root.get<Long>("legacyTransactionId"), it)) }
    transactionType?.let { predicates.add(cb.equal(root.get<String>("transactionType"), it)) }
    startDate?.let { predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), it)) }
    endDate?.let { predicates.add(cb.lessThan(root.get("timestamp"), it)) }

    if (cursorTimestamp != null && cursorId != null) {
      val beforeCursor = cb.lessThan(root.get<Instant>("timestamp"), cursorTimestamp)
      val sameTimestampSmallerId = cb.and(
        cb.equal(root.get<Instant>("timestamp"), cursorTimestamp),
        cb.lessThan(root.get<Long>("id"), cursorId),
      )
      predicates.add(cb.or(beforeCursor, sameTimestampSmallerId))
    }

    if (predicates.isNotEmpty()) {
      query.where(*predicates.toTypedArray())
    }

    query.orderBy(
      cb.desc(root.get<Instant>("timestamp")),
      cb.desc(root.get<Long>("id")),
    )

    val typedQuery = em.createQuery(query)
    typedQuery.firstResult = pageable.offset.toInt()
    typedQuery.maxResults = pageable.pageSize

    return typedQuery.resultList
  }

  fun countMatchingPayloads(
    prisonId: String?,
    legacyTransactionId: Long?,
    transactionType: String?,
    startDate: Instant?,
    endDate: Instant?,
  ): Long {
    val cb = em.criteriaBuilder
    val query = cb.createQuery(Long::class.java)
    val root = query.from(NomisSyncPayload::class.java)

    val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

    prisonId?.let { predicates.add(cb.equal(root.get<String>("caseloadId"), it)) }
    legacyTransactionId?.let { predicates.add(cb.equal(root.get<Long>("legacyTransactionId"), it)) }
    transactionType?.let { predicates.add(cb.equal(root.get<String>("transactionType"), it)) }
    startDate?.let { predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), it)) }
    endDate?.let { predicates.add(cb.lessThan(root.get("timestamp"), it)) }

    query.select(cb.count(root))
    if (predicates.isNotEmpty()) {
      query.where(*predicates.toTypedArray())
    }

    return em.createQuery(query).singleResult
  }
}
