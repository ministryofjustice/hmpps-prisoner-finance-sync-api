package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models

import java.math.BigDecimal

interface AccountDetails {
  val code: Int
  val name: String
  val balance: BigDecimal
}
