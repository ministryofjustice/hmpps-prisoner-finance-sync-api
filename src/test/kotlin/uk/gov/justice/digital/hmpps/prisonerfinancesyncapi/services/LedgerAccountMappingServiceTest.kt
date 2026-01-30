package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LedgerAccountMappingServiceTest {

  private val mappingService = LedgerAccountMappingService()

  @Nested
  @DisplayName("mapPrisonerSubAccount")
  inner class MapPrisonerSubAccount {

    @ParameterizedTest
    @CsvSource(
      "2101, CASH",
      "2102, SPENDS",
      "2103, SAVINGS",
    )
    fun `should map NOMIS prisoner account codes to GL account reference`(accountCode: Int, expectedReference: String) {
      val result = mappingService.mapPrisonerSubAccount(accountCode)
      assertThat(result).isEqualTo(expectedReference)
    }

    @Test
    fun `should throw exception for unknown prisoner code`() {
      val unknownCode = 9999
      assertThatThrownBy {
        mappingService.mapPrisonerSubAccount(unknownCode)
      }.isInstanceOf(IllegalArgumentException::class.java)
    }
  }

  @Nested
  @DisplayName("mapPrisonSubAccount")
  inner class MapPrisonSubAccount {

    @ParameterizedTest
    @CsvSource(
      "1502, ADV,  1502:ADV",
      "2501, CANT, 2501:CANT",
      "2199, HOA,  2199:HOA",
      "2199, WHF,  2199:WHF",
      "7000, FRED, 7000:FRED",
    )
    fun `should map NOMIS prison account code and transaction type to GL account reference`(
      accountCode: Int,
      txnType: String,
      expectedReference: String,
    ) {
      val result = mappingService.mapPrisonSubAccount(accountCode, txnType)
      assertThat(result).isEqualTo(expectedReference)
    }
  }

  @Nested
  @DisplayName("mapSubAccountGLReferenceToNOMIS")
  inner class MapSubAccountGLReferenceToNOMIS {

    @ParameterizedTest
    @CsvSource(
      "1502:ADV, 1502, ADV",
      "2501:CANT, 2501, CANT",
      "2199:HOA, 2199, HOA",
      "2199:WHF, 2199, WHF",
      "7000:FRED, 7000, FRED",
    )
    fun `should map GL account reference to NOMIS prison account code, and transaction type`(
      inputString: String,
      expectedCode: Int,
      expectedTxType: String,
    ) {
      val result = mappingService.mapSubAccountGLReferenceToNOMIS(inputString)
      assertThat(result.code).isEqualTo(expectedCode)
      assertThat(result.txType).isEqualTo(expectedTxType)
    }

    @ParameterizedTest
    @CsvSource(
      "BAD:REF",
      "BAD:REF:REF",
    )
    fun `should handle illegal GL references`(arg: String) {
      assertThrows<IllegalArgumentException> { mappingService.mapSubAccountGLReferenceToNOMIS(arg) }
    }
  }

  @Nested
  @DisplayName("mapSubAccountPrisonerReferenceToNOMIS")
  inner class MapSubAccountPrisonerReferenceToNOMIS {

    @ParameterizedTest
    @CsvSource(
      "CASH, 2101",
      "SPENDS, 2102",
      "SAVINGS, 2103",
    )
    fun `should map NOMIS prisoner account codes to GL account reference`(inputReference: String, expectedCode: Int) {
      val result = mappingService.mapSubAccountPrisonerReferenceToNOMIS(inputReference)
      assertThat(result).isEqualTo(expectedCode)
    }

    @Test
    fun `should throw exception for unknown prisoner code`() {
      val unknownCode = "HELLOWORLD"
      assertThatThrownBy {
        mappingService.mapSubAccountPrisonerReferenceToNOMIS(unknownCode)
      }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @ParameterizedTest
    @CsvSource(
      "000, false",
      "2101, true",
      "2102, true",
      "2103, true",
      "999, false",
      "666, false",
      "911, false",
    )
    fun `should return true if is valid prisoner account code`(prisonCode: Int, isValid: Boolean) {
      val result = mappingService.isValidPrisonerAccountCode(prisonCode)

      assertThat(result).isEqualTo(isValid)
    }
  }
}
