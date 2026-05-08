package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service

@Service
class MetricsService(private val meterRegistry: MeterRegistry) {
  fun registerCounter(meterRegistry: MeterRegistry, name: String): Counter {
    val builder = Counter.builder(name)
    return builder.register(meterRegistry)
  }
}
