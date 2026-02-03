package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.toGLPostingType

class PostingTypeTest {
  @ParameterizedTest
  @CsvSource(
    "CR, CR",
    "DR, DR",
  )
  fun `should cast string PostingType to Enum`(inputVal: String, postingType: PostingType) {
    assertThat(inputVal.toGLPostingType()).isEqualTo(postingType)
  }

  @ParameterizedTest
  @CsvSource(
    "BOB",
    "''",
  )
  fun `Should throw exception when postingType not valid`(inputVal: String) {
    assertThatThrownBy {
      inputVal.toGLPostingType()
    }.isInstanceOf(IllegalArgumentException::class.java).hasMessage("Invalid posting type $inputVal")
  }
}
