package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.exceptions.GeneralLedgerAccountNotFoundException
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestControllerAdvice
class PrisonerFinanceSyncApiExceptionHandler {

  @ExceptionHandler(CustomException::class)
  fun handleCustomException(e: CustomException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(e.status)
    .body(
      ErrorResponse(
        status = e.status.value(),
        userMessage = e.message,
        developerMessage = e.message,
      ),
    ).also { log.info("CustomExceptionThrown: {}", e.message) }

  @ExceptionHandler(GeneralLedgerAccountNotFoundException::class)
  fun handleGeneralLedgerAccountNotFoundException(e: GeneralLedgerAccountNotFoundException): ResponseEntity<ErrorResponse> {
    val userMessage = "Not Found: " + e.message
    val developerMessage = "Not Found " + e.message
    return ResponseEntity
      .status(NOT_FOUND)
      .body(
        ErrorResponse(
          status = NOT_FOUND,
          userMessage = userMessage,
          developerMessage = developerMessage,
        ),
      ).also { log.info("No resource found exception: {}", e.message) }
  }

  @ExceptionHandler(HttpMessageNotReadableException::class)
  fun handleHttpMessageNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
    log.warn("HttpMessageNotReadableException caught: {}", e.message)
    val userMessage = "Invalid request body: " + (e.rootCause?.message ?: e.message)
    val developerMessage = "JSON parse error: " + (e.rootCause?.message ?: e.message)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = userMessage,
          developerMessage = developerMessage,
        ),
      ).also { log.info("Bad request - HttpMessageNotReadableException: {}", e.message) }
  }

  @ExceptionHandler(MissingServletRequestParameterException::class)
  fun handleMissingServletRequestParameterException(e: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> {
    log.warn("MissingServletRequestParameterException caught: {}", e.message)
    val userMessage = "Invalid request, a required parameter is missing"
    val developerMessage = "JSON parse error: " + e.message
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = userMessage,
          developerMessage = developerMessage,
        ),
      ).also { log.info("Bad request - MissingServletRequestParameterException: {}", e.message) }
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException::class)
  fun handleMethodArgumentTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
    val paramName = e.parameter.parameterName
    val requiredType = e.parameter.parameterType.simpleName

    val userMessage = "Parameter '$paramName' must be of type $requiredType"

    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = userMessage,
          developerMessage = e.message,
        ),
      ).also { log.info("MethodArgumentTypeMismatchException: {}", e.message) }
  }

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
    val errors = ex.bindingResult.fieldErrors.joinToString(separator = ", ") { "${it.field}: ${it.defaultMessage}" }
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Validation failure",
          developerMessage = "Validation failed: $errors",
        ),
      ).also { log.info("MethodArgumentNotValidException: {}", errors) }
  }

  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: ValidationException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(BAD_REQUEST)
    .body(
      ErrorResponse(
        status = BAD_REQUEST,
        userMessage = "Validation failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.info("Validation exception: {}", e.message) }

  @ExceptionHandler(IllegalArgumentException::class)
  fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(BAD_REQUEST)
    .body(
      ErrorResponse(
        status = BAD_REQUEST,
        userMessage = "Illegal Argument failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.info("Illegal Argument exception: {}", e.message) }

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleNoResourceFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(NOT_FOUND)
    .body(
      ErrorResponse(
        status = NOT_FOUND,
        userMessage = "No resource found failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.info("No resource found exception: {}", e.message) }

  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(FORBIDDEN)
    .body(
      ErrorResponse(
        status = FORBIDDEN,
        userMessage = "Forbidden: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.debug("Forbidden (403) returned: {}", e.message) }

  @ExceptionHandler(Exception::class)
  fun handleException(e: Exception): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(INTERNAL_SERVER_ERROR)
    .body(
      ErrorResponse(
        status = INTERNAL_SERVER_ERROR,
        userMessage = "Unexpected error: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.error("Unexpected exception: [${e.message}]", e) }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
