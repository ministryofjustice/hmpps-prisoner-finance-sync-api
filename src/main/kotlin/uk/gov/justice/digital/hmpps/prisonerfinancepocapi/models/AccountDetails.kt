package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.models

import java.math.BigDecimal

interface AccountDetails {
  val code: Int
  val name: String
  val balance: BigDecimal
}
