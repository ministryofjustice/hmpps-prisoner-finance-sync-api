package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.migration

import org.openapitools.client.infrastructure.Serializer
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse

class MigrationBalanceValidationMismatchEvent(
  private val prisonNumber: String,
  private val nomisBalances: List<PrisonerAccountPointInTimeBalance>,
  private val aggregatedNomisBalances: Map<Int, PrisonerAccountPointInTimeAggregatedBalance>,
  private val generalLedgerBalances: Map<String, SubAccountBalanceResponse>,
) {
  val eventName: String = "prisoner-finance-sync-api-balance-validation-mismatch"

  fun toStringMap(): Map<String, String> {
    val mapper: (Any) -> String = { Serializer.jacksonObjectMapper.writeValueAsString(it) }
    return mapOf(
      "prisonerNumber" to prisonNumber,
      "nomisBalances" to mapper(nomisBalances),
      "aggregatedNomisBalances" to mapper(aggregatedNomisBalances),
      "generalLedgerBalances" to mapper(generalLedgerBalances),
    )
  }
}
