package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PrisonerFinanceSyncApi

fun main(args: Array<String>) {
  runApplication<PrisonerFinanceSyncApi>(*args)
}
