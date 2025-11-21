package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JsonComparatorTest {

  private lateinit var objectMapper: ObjectMapper
  private lateinit var jsonComparator: JsonComparator

  @BeforeEach
  fun setUp() {
    objectMapper = ObjectMapper()
    jsonComparator = JsonComparator(objectMapper)
  }

  @Nested
  inner class AreJsonBodiesEqualTests {

    @Test
    fun `should return true for identical JSON bodies`() {
      val json1 = """{"id": 1, "name": "test"}"""
      val json2 = """{"id": 1, "name": "test"}"""
      assertTrue(jsonComparator.areJsonBodiesEqual(json1, json2))
    }

    @Test
    fun `should return true when only ignored fields differ`() {
      val json1 = """{"id": 1, "name": "test", "requestId": "abc-123"}"""
      val json2 = """{"id": 1, "name": "test", "requestId": "xyz-456"}"""
      assertTrue(jsonComparator.areJsonBodiesEqual(json1, json2))
    }

    @Test
    fun `should return false when a non-ignored field differs`() {
      val json1 = """{"id": 1, "name": "test1"}"""
      val json2 = """{"id": 1, "name": "test2"}"""
      assertFalse(jsonComparator.areJsonBodiesEqual(json1, json2))
    }

    @Test
    fun `should return false when JSON structure is different`() {
      val json1 = """{"id": 1, "name": "test"}"""
      val json2 = """{"id": 1, "description": "test"}"""
      assertFalse(jsonComparator.areJsonBodiesEqual(json1, json2))
    }

    @Test
    fun `should return false for invalid JSON strings`() {
      val json1 = """{"id": 1, "name": "test"}"""
      val json2 = """{"id": 1, "name": "test",}""" // Trailing comma makes it invalid
      assertFalse(jsonComparator.areJsonBodiesEqual(json1, json2))
    }

    @Test
    fun `should handle empty JSON bodies`() {
      val json1 = "{}"
      val json2 = "{}"
      assertTrue(jsonComparator.areJsonBodiesEqual(json1, json2))
    }
  }

  @Nested
  inner class CompareMapsTests {

    @Test
    fun `should return true when maps are identical`() {
      val map1 = mapOf("id" to 1, "name" to "test")
      val map2 = mapOf("id" to 1, "name" to "test")
      assertTrue(jsonComparator.compareMaps(map1, map2))
    }

    @Test
    fun `should return true when maps are equal after filtering ignored fields`() {
      val map1 = mapOf("id" to 1, "name" to "test", "requestId" to "a")
      val map2 = mapOf("id" to 1, "name" to "test", "requestId" to "b")
      assertTrue(jsonComparator.compareMaps(map1, map2))
    }

    @Test
    fun `should return false when a non-ignored value is different`() {
      val map1 = mapOf("id" to 1, "name" to "test1")
      val map2 = mapOf("id" to 1, "name" to "test2")
      assertFalse(jsonComparator.compareMaps(map1, map2))
    }

    @Test
    fun `should handle case sensitivity for keys correctly`() {
      val map1 = mapOf("ID" to 1)
      val map2 = mapOf("id" to 1)
      assertFalse(jsonComparator.compareMaps(map1, map2))
    }

    @Test
    fun `should work with nested objects and arrays`() {
      val map1 = mapOf("data" to mapOf("items" to listOf(1, 2)), "requestId" to "a")
      val map2 = mapOf("data" to mapOf("items" to listOf(1, 2)), "requestId" to "b")
      assertTrue(jsonComparator.compareMaps(map1, map2))
    }

    @Test
    fun `should return false when nested content differs`() {
      val map1 = mapOf("data" to mapOf("items" to listOf(1, 2)))
      val map2 = mapOf("data" to mapOf("items" to listOf(1, 3)))
      assertFalse(jsonComparator.compareMaps(map1, map2))
    }

    @Test
    fun `should return false if one map contains a key not in the other`() {
      val map1 = mapOf("id" to 1, "name" to "test")
      val map2 = mapOf("id" to 1)
      assertFalse(jsonComparator.compareMaps(map1, map2))
    }
  }
}
