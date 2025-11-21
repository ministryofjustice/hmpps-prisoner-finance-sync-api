package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class JsonComparator(private val objectMapper: ObjectMapper) {

  private val log = LoggerFactory.getLogger(JsonComparator::class.java)

  var ignoredFields: Set<String> = setOf("requestId")

  fun areJsonBodiesEqual(storedJson: String, newJson: String): Boolean {
    return try {
      val storedMap = jsonToMap(storedJson)
      val newMap = jsonToMap(newJson)

      return compareMaps(storedMap, newMap)
    } catch (e: Exception) {
      log.error("Failed to compare JSON bodies", e)
      false
    }
  }

  private fun jsonToMap(jsonString: String): Map<String, Any?> = objectMapper.readValue(jsonString)

  fun compareMaps(map1: Map<String, Any?>, map2: Map<String, Any?>): Boolean {
    val map1Cleaned = map1.filterKeys { it !in ignoredFields }
    val map2Cleaned = map2.filterKeys { it !in ignoredFields }
    return map1Cleaned == map2Cleaned
  }
}
