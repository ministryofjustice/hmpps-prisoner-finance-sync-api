package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

class PrisonerFinanceSyncApiExceptionHandlerTest {
  private val handler = PrisonerFinanceSyncApiExceptionHandler()

  @Test
  fun `should return BAD_REQUEST with correct error response when MethodArgumentTypeMismatchException occurs`() {
    val methodParameter = mock<MethodParameter>()
    val paramName = "age"
    whenever(methodParameter.parameterName).thenReturn(paramName)
    whenever(methodParameter.parameterType).thenReturn(Int::class.java)

    val exception = MethodArgumentTypeMismatchException(
      "abc",
      Int::class.java,
      paramName,
      methodParameter,
      IllegalArgumentException("Invalid value"),
    )

    val response = handler.handleMethodArgumentTypeMismatchException(exception)

    assertEquals(BAD_REQUEST, response.statusCode)

    val body = response.body!!
    assertEquals(400, body.status)
    assertEquals(
      "Parameter '$paramName' must be of type int",
      body.userMessage,
    )
    assertEquals(exception.message, body.developerMessage)
  }
}
