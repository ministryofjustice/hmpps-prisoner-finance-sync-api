package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import io.micrometer.core.instrument.Counter
import org.springframework.stereotype.Service
import io.micrometer.core.instrument.MeterRegistry

@Service
class MetricsService (private val meterRegistry: MeterRegistry) {
  fun registerCounter(meterRegistry: MeterRegistry, name: String): Counter {
    val builder = Counter.builder(name)
    return builder.register(meterRegistry)
  }
}