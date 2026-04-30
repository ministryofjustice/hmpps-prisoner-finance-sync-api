package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger

import org.openapitools.client.infrastructure.Serializer
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.PrisonerEstablishmentBalanceDetails

data class GeneralLedgerDiscrepancyDetails(
  val message: String,
  val prisonerId: String,
  val accountType: String,
  val legacyAggregatedBalance: Long,
  val generalLedgerBalance: Long,
  val discrepancy: Long,
  val glBreakdown: List<SubAccountBalanceResponse>,
  val legacyBreakdown: List<PrisonerEstablishmentBalanceDetails>,
) {

  fun toStringMap(): Map<String, String> {
    val mapper: (Any) -> String = { Serializer.jacksonObjectMapper.writeValueAsString(it) }
    return mapOf(
      "message" to message,
      "prisonerId" to prisonerId,
      "accountType" to accountType,
      "legacyAggregatedBalance" to mapper(legacyAggregatedBalance),
      "generalLedgerBalance" to mapper(generalLedgerBalance),
      "discrepancy" to discrepancy.toString(),
      "glBreakdown" to mapper(glBreakdown),
      "legacyBreakdown" to mapper(legacyBreakdown),
    )
  }
}

data class DiscrepancyProperties(
  val message: String,
  val prisonerId: String,
  val accountType: String,
  val glBreakdown: List<SubAccountBalanceResponse>,
  val legacyBreakdown: List<PrisonerEstablishmentBalanceDetails>,
) {

  fun toStringMap(): Map<String, String> {
    val mapper: (Any) -> String = { Serializer.jacksonObjectMapper.writeValueAsString(it) }
    return mapOf(
      "message" to message,
      "prisonerId" to prisonerId,
      "accountType" to accountType,
      "glBreakdown" to mapper(glBreakdown),
      "legacyBreakdown" to mapper(legacyBreakdown),
    )
  }
}
