package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.audit

data class CursorPage<T>(
  val content: List<T>,
  val nextCursor: String?,
  val totalElements: Long,
  val size: Int,
)
