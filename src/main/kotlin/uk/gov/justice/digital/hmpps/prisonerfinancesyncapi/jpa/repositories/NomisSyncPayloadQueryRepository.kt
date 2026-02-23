package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit.NomisSyncPayloadSummary
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class NomisSyncPayloadQueryRepository(
  private val jdbcTemplate: NamedParameterJdbcTemplate,
) {

  fun findMatchingPayloads(
    prisonId: String?,
    legacyTransactionId: Long?,
    transactionType: String?,
    startDate: Instant?,
    endDate: Instant?,
    cursorTimestamp: Instant?,
    cursorId: Long?,
    limit: Int,
  ): List<NomisSyncPayloadSummary> {
    val params = MapSqlParameterSource("limit", limit)

    val sql = buildString {
      append(
        """
        SELECT id, legacy_transaction_id, transaction_type, synchronized_transaction_id, 
               caseload_id, timestamp, request_type_identifier, request_id, transaction_timestamp
        FROM nomis_sync_payloads 
        WHERE 1=1
      """,
      )

      appendFilters(this, params, prisonId, legacyTransactionId, transactionType, startDate, endDate)

      if (cursorTimestamp != null && cursorId != null) {
        append(" AND (timestamp < :cursorTimestamp OR (timestamp = :cursorTimestamp AND id < :cursorId))")
        params.addValue("cursorTimestamp", Timestamp.from(cursorTimestamp))
        params.addValue("cursorId", cursorId)
      }

      append(" ORDER BY timestamp DESC, id DESC LIMIT :limit")
    }

    return jdbcTemplate.query(sql, params, summaryRowMapper)
  }

  fun countMatchingPayloads(
    prisonId: String?,
    legacyTransactionId: Long?,
    transactionType: String?,
    startDate: Instant?,
    endDate: Instant?,
  ): Long {
    val params = MapSqlParameterSource()
    val sql = buildString {
      append("SELECT count(id) FROM nomis_sync_payloads WHERE 1=1")
      appendFilters(this, params, prisonId, legacyTransactionId, transactionType, startDate, endDate)
    }
    return jdbcTemplate.queryForObject(sql, params, Long::class.java) ?: 0L
  }

  private fun appendFilters(
    sql: StringBuilder,
    params: MapSqlParameterSource,
    prisonId: String?,
    legacyTransactionId: Long?,
    transactionType: String?,
    startDate: Instant?,
    endDate: Instant?,
  ) {
    prisonId?.let {
      sql.append(" AND caseload_id = :prisonId")
      params.addValue("prisonId", it)
    }
    legacyTransactionId?.let {
      sql.append(" AND legacy_transaction_id = :legacyId")
      params.addValue("legacyId", it)
    }
    transactionType?.let {
      sql.append(" AND transaction_type = :txnType")
      params.addValue("txnType", it)
    }
    startDate?.let {
      sql.append(" AND timestamp >= :startDate")
      params.addValue("startDate", Timestamp.from(it))
    }
    endDate?.let {
      sql.append(" AND timestamp < :endDate")
      params.addValue("endDate", Timestamp.from(it))
    }
  }

  private val summaryRowMapper = { rs: ResultSet, _: Int ->
    object : NomisSyncPayloadSummary {
      override val id = rs.getLong("id")
      override val legacyTransactionId = rs.getLong("legacy_transaction_id").takeIf { !rs.wasNull() }
      override val transactionType = rs.getString("transaction_type")
      override val synchronizedTransactionId = UUID.fromString(rs.getString("synchronized_transaction_id"))
      override val caseloadId = rs.getString("caseload_id")
      override val timestamp = rs.getTimestamp("timestamp").toInstant()
      override val requestTypeIdentifier = rs.getString("request_type_identifier")
      override val requestId = UUID.fromString(rs.getString("request_id"))
      override val transactionTimestamp = rs.getTimestamp("transaction_timestamp")?.toInstant()
    }
  }
}
