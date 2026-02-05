package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlSubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.GlTransactionRequest
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import java.util.function.Function

@ExtendWith(MockitoExtension::class)
@DisplayName("General Ledger Api Client Test")
class GeneralLedgerApiClientTest {

  @Mock
  private lateinit var webClient: WebClient

  @Mock
  private lateinit var requestHeadersUriSpec: RequestHeadersUriSpec<*>

  @Mock
  private lateinit var requestBodyUriSpec: RequestBodyUriSpec

  @Mock
  private lateinit var requestHeadersSpec: RequestHeadersSpec<*>

  @Mock
  private lateinit var responseSpec: ResponseSpec

  @InjectMocks
  private lateinit var apiClient: GeneralLedgerApiClient

  @Nested
  inner class FindSubAccount {
    @Test
    fun `should return first sub-account when found`() {
      val parentRef = "A1234AA"
      val subRef = "SPND"
      val expectedResponse = GlSubAccountResponse(UUID.randomUUID(), UUID.randomUUID(), subRef, LocalDateTime.now(), "user")

      mockWebClientGetChain(listOf(expectedResponse))

      val result = apiClient.findSubAccount(parentRef, subRef)

      assertThat(result).isEqualTo(expectedResponse)
    }

    @Test
    fun `should return null when list is empty (Not Found)`() {
      mockWebClientGetChain(emptyList<GlSubAccountResponse>())

      val result = apiClient.findSubAccount("A1234AA", "SPND")

      assertThat(result).isNull()
    }

    @Test
    fun `should return null if response body is null`() {
      whenever(webClient.get()).thenReturn(requestHeadersUriSpec)
      whenever(requestHeadersUriSpec.uri(any<Function<UriBuilder, URI>>())).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
      whenever(responseSpec.bodyToMono(any<ParameterizedTypeReference<List<GlSubAccountResponse>>>()))
        .thenReturn(Mono.empty())

      assertThat(apiClient.findSubAccount("A1234AA", "SPND")).isNull()
    }

    private fun mockWebClientGetChain(response: List<GlSubAccountResponse>) {
      whenever(webClient.get()).thenReturn(requestHeadersUriSpec)
      whenever(requestHeadersUriSpec.uri(any<Function<UriBuilder, URI>>())).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
      whenever(responseSpec.bodyToMono(any<ParameterizedTypeReference<List<GlSubAccountResponse>>>()))
        .thenReturn(Mono.just(response))
    }
  }

  @Nested
  inner class CreateSubAccount {
    @Test
    fun `should create sub-account successfully`() {
      val parentId = UUID.randomUUID()
      val subRef = "SPND"
      val expectedResponse = GlSubAccountResponse(UUID.randomUUID(), parentId, subRef, LocalDateTime.now(), "user")

      mockWebClientPostChain(expectedResponse)

      val result = apiClient.createSubAccount(parentId, subRef)

      assertThat(result).isEqualTo(expectedResponse)
      verify(requestBodyUriSpec).bodyValue(GlSubAccountRequest(subRef))
    }

    @Test
    fun `should throw IllegalStateException if response body is null`() {
      val parentId = UUID.randomUUID()
      whenever(webClient.post()).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.uri(any<String>(), any<UUID>())).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.bodyValue(any<GlSubAccountRequest>())).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
      whenever(responseSpec.bodyToMono(GlSubAccountResponse::class.java)).thenReturn(Mono.empty())

      assertThatThrownBy { apiClient.createSubAccount(parentId, "SPND") }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("Received null response")
    }

    @Test
    fun `should throw exception if parent account not found`() {
      val parentId = UUID.randomUUID()
      whenever(webClient.post()).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.uri(any<String>(), any<UUID>())).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.bodyValue(any<GlSubAccountRequest>())).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
      whenever(responseSpec.bodyToMono(GlSubAccountResponse::class.java))
        .thenThrow(WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "Not Found", HttpHeaders.EMPTY, ByteArray(0), null))

      assertThatThrownBy { apiClient.createSubAccount(parentId, "NOT_FOUND") }
        .isInstanceOf(WebClientResponseException.NotFound::class.java)
    }

    private fun mockWebClientPostChain(response: GlSubAccountResponse) {
      whenever(webClient.post()).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.uri(any<String>(), any<UUID>())).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.bodyValue(any<GlSubAccountRequest>())).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
      whenever(responseSpec.bodyToMono(GlSubAccountResponse::class.java)).thenReturn(Mono.just(response))
    }
  }

  @Nested
  inner class FindAccountByReference {
    @Test
    fun `should return account when found`() {
      val ref = "A1234AA"
      val expectedResponse = GlAccountResponse(UUID.randomUUID(), ref, LocalDateTime.now(), "user", emptyList())

      mockWebClientGetChain(listOf(expectedResponse))

      val result = apiClient.findAccountByReference(ref)

      assertThat(result).isEqualTo(expectedResponse)
    }

    @Test
    fun `should return null when list is empty (Not Found)`() {
      mockWebClientGetChain(emptyList<GlAccountResponse>())

      val result = apiClient.findAccountByReference("UNKNOWN")

      assertThat(result).isNull()
    }

    @Test
    fun `should return null if response body is null`() {
      whenever(webClient.get()).thenReturn(requestHeadersUriSpec)
      whenever(requestHeadersUriSpec.uri(any<Function<UriBuilder, URI>>())).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
      whenever(responseSpec.bodyToMono(any<ParameterizedTypeReference<List<GlAccountResponse>>>()))
        .thenReturn(Mono.empty())

      assertThat(apiClient.findAccountByReference("A1234AA")).isNull()
    }

    private fun mockWebClientGetChain(response: List<GlAccountResponse>) {
      whenever(webClient.get()).thenReturn(requestHeadersUriSpec)
      whenever(requestHeadersUriSpec.uri(any<Function<UriBuilder, URI>>())).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
      whenever(responseSpec.bodyToMono(any<ParameterizedTypeReference<List<GlAccountResponse>>>()))
        .thenReturn(Mono.just(response))
    }
  }

  @Nested
  inner class CreateAccount {
    @Test
    fun `should return created account on success`() {
      val ref = "A1234AA"
      val expectedResponse = GlAccountResponse(UUID.randomUUID(), ref, LocalDateTime.now(), "user", emptyList())

      mockWebClientPostChain(expectedResponse)

      val result = apiClient.createAccount(ref)

      assertThat(result).isEqualTo(expectedResponse)
    }

    @Test
    fun `should throw exception on 400 Bad Request (Duplicate)`() {
      whenever(webClient.post()).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.bodyValue(any<GlAccountRequest>())).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)

      val badRequestEx = WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "Bad Req", HttpHeaders.EMPTY, ByteArray(0), null)
      whenever(responseSpec.bodyToMono(GlAccountResponse::class.java)).thenThrow(badRequestEx)

      assertThatThrownBy {
        apiClient.createAccount("A1234AA")
      }.isInstanceOf(WebClientResponseException::class.java)
    }

    @Test
    fun `should throw IllegalStateException if response body is null`() {
      whenever(webClient.post()).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.bodyValue(any<GlAccountRequest>())).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
      whenever(responseSpec.bodyToMono(GlAccountResponse::class.java)).thenReturn(Mono.empty())

      assertThatThrownBy { apiClient.createAccount("A1234AA") }
        .isInstanceOf(IllegalStateException::class.java)
    }

    private fun mockWebClientPostChain(response: GlAccountResponse) {
      whenever(webClient.post()).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.bodyValue(any<GlAccountRequest>())).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
      whenever(responseSpec.bodyToMono(GlAccountResponse::class.java)).thenReturn(Mono.just(response))
    }
  }

  @Nested
  inner class PostTransaction {
    @Test
    fun `should post transaction with idempotency key and return ID`() {
      val txId = UUID.randomUUID()
      val idempotencyKey = UUID.randomUUID()
      val request = GlTransactionRequest(
        reference = "REF",
        description = "Desc",
        timestamp = Instant.now(),
        amount = 100L,
        postings = listOf(),
      )
      val receipt = GlTransactionReceipt(txId, "REF", 100L)

      whenever(webClient.post()).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodyUriSpec)

      whenever(requestBodyUriSpec.header(eq("Idempotency-Key"), eq(idempotencyKey.toString())))
        .thenReturn(requestBodyUriSpec)

      whenever(requestBodyUriSpec.bodyValue(eq(request))).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
      whenever(responseSpec.bodyToMono(GlTransactionReceipt::class.java)).thenReturn(Mono.just(receipt))

      val result = apiClient.postTransaction(request, idempotencyKey)

      assertThat(result).isEqualTo(txId)

      verify(requestBodyUriSpec).header("Idempotency-Key", idempotencyKey.toString())
    }

    @Test
    fun `should throw IllegalStateException if response body is null`() {
      val idempotencyKey = UUID.randomUUID()
      val request = GlTransactionRequest(
        reference = "REF",
        description = "Desc",
        timestamp = Instant.now(),
        amount = 100L,
        postings = listOf(),
      )

      whenever(webClient.post()).thenReturn(requestBodyUriSpec)
      whenever(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodyUriSpec)

      whenever(requestBodyUriSpec.header(eq("Idempotency-Key"), any()))
        .thenReturn(requestBodyUriSpec)

      whenever(requestBodyUriSpec.bodyValue(any())).thenReturn(requestHeadersSpec)
      whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
      whenever(responseSpec.bodyToMono(GlTransactionReceipt::class.java)).thenReturn(Mono.empty())

      assertThatThrownBy {
        apiClient.postTransaction(request, idempotencyKey)
      }.isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("returned null body")
    }
  }
}
