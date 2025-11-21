package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models

import java.math.BigDecimal

data class PrisonerEstablishmentBalance(
  val prisonId: String,
  val totalBalance: BigDecimal,
  val holdBalance: BigDecimal,
)
