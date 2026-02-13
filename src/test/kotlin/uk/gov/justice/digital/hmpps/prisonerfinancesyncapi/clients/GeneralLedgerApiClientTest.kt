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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.AccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.SubAccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.clients.generalledger.TransactionControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.TransactionResponse
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@DisplayName("General Ledger Api Client Test")
class GeneralLedgerApiClientTest {

  @Mock
  private lateinit var accountApi: AccountControllerApi

  @Mock
  private lateinit var subAccountApi: SubAccountControllerApi

  @Mock
  private lateinit var transactionApi: TransactionControllerApi

  @InjectMocks
  private lateinit var apiClient: GeneralLedgerApiClient

  @Nested
  inner class FindSubAccount {
    @Test
    fun `should return first sub-account when found`() {
      val parentRef = "A1234AA"
      val subRef = "SPND"
      val expectedResponse = SubAccountResponse(
        id = UUID.randomUUID(),
        parentAccountId = UUID.randomUUID(),
        reference = subRef,
        createdAt = Instant.now(),
        createdBy = "user",
      )

      whenever(subAccountApi.findSubAccounts(subRef, parentRef))
        .thenReturn(Mono.just(listOf(expectedResponse)))

      val result = apiClient.findSubAccount(parentRef, subRef)

      assertThat(result).isEqualTo(expectedResponse)
    }

    @Test
    fun `should return null when list is empty (Not Found)`() {
      whenever(subAccountApi.findSubAccounts("SPND", "A1234AA"))
        .thenReturn(Mono.just(emptyList()))

      val result = apiClient.findSubAccount("A1234AA", "SPND")

      assertThat(result).isNull()
    }

    @Test
    fun `should return null if response body is null`() {
      whenever(subAccountApi.findSubAccounts(any(), any()))
        .thenReturn(Mono.empty())

      assertThat(apiClient.findSubAccount("A1234AA", "SPND")).isNull()
    }
  }

  @Nested
  inner class CreateSubAccount {
    @Test
    fun `should create sub-account successfully`() {
      val parentId = UUID.randomUUID()
      val subRef = "SPND"
      val expectedResponse = SubAccountResponse(
        id = UUID.randomUUID(),
        parentAccountId = parentId,
        reference = subRef,
        createdAt = Instant.now(),
        createdBy = "user",
      )

      val expectedRequest = CreateSubAccountRequest(subAccountReference = subRef)

      whenever(subAccountApi.createSubAccount(parentId, expectedRequest))
        .thenReturn(Mono.just(expectedResponse))

      val result = apiClient.createSubAccount(parentId, subRef)

      assertThat(result).isEqualTo(expectedResponse)
      verify(subAccountApi).createSubAccount(parentId, expectedRequest)
    }

    @Test
    fun `should throw IllegalStateException if response body is null`() {
      val parentId = UUID.randomUUID()
      whenever(subAccountApi.createSubAccount(any(), any())).thenReturn(Mono.empty())

      assertThatThrownBy { apiClient.createSubAccount(parentId, "SPND") }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("Received null response")
    }

    @Test
    fun `should throw exception if parent account not found`() {
      val parentId = UUID.randomUUID()

      val notFoundEx = WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "Not Found", HttpHeaders(), ByteArray(0), null)
      whenever(subAccountApi.createSubAccount(any(), any())).thenReturn(Mono.error(notFoundEx))

      assertThatThrownBy { apiClient.createSubAccount(parentId, "NOT_FOUND") }
        .isInstanceOf(WebClientResponseException.NotFound::class.java)
    }
  }

  @Nested
  inner class FindAccountBalanceByAccountID {
    @Test
    fun `should return balance when found`() {
      val accountId = UUID.randomUUID()
      val expectedResponse = SubAccountBalanceResponse(
        subAccountId = accountId,
        balanceDateTime = Instant.now(),
        amount = 1000L,
      )

      whenever(subAccountApi.getSubAccountBalance(accountId))
        .thenReturn(Mono.just(expectedResponse))

      val result = apiClient.findSubAccountBalanceByAccountId(accountId)

      assertThat(result).isEqualTo(expectedResponse)
      verify(subAccountApi).getSubAccountBalance(accountId)
    }

    @Test
    fun `should throw exception when account does not exist`() {
      val accountId = UUID.randomUUID()

      whenever(subAccountApi.getSubAccountBalance(accountId))
        .thenReturn(
          Mono.error(
            WebClientResponseException.create(
              HttpStatus.NOT_FOUND.value(),
              "Not Found",
              HttpHeaders.EMPTY,
              ByteArray(0),
              null,
            ),
          ),
        )

      assertThatThrownBy { apiClient.findSubAccountBalanceByAccountId(accountId) }
        .isInstanceOf(WebClientResponseException.NotFound::class.java)
    }
  }

  @Nested
  inner class FindAccountByReference {
    @Test
    fun `should return account when found`() {
      val ref = "A1234AA"
      val expectedResponse = AccountResponse(
        id = UUID.randomUUID(),
        reference = ref,
        createdAt = Instant.now(),
        createdBy = "user",
        subAccounts = emptyList(),
      )

      whenever(accountApi.getAccount(ref)).thenReturn(Mono.just(listOf(expectedResponse)))

      val result = apiClient.findAccountByReference(ref)

      assertThat(result).isEqualTo(expectedResponse)
      verify(accountApi).getAccount(ref)
    }

    @Test
    fun `should return null when list is empty (Not Found)`() {
      whenever(accountApi.getAccount("UNKNOWN")).thenReturn(Mono.just(emptyList()))

      val result = apiClient.findAccountByReference("UNKNOWN")

      assertThat(result).isNull()
    }

    @Test
    fun `should return null if response body is null`() {
      whenever(accountApi.getAccount("A1234AA")).thenReturn(Mono.empty())

      assertThat(apiClient.findAccountByReference("A1234AA")).isNull()
    }
  }

  @Nested
  inner class CreateAccount {
    @Test
    fun `should return created account on success`() {
      val ref = "A1234AA"
      val expectedResponse = AccountResponse(
        id = UUID.randomUUID(),
        reference = ref,
        createdAt = Instant.now(),
        createdBy = "user",
        subAccounts = emptyList(),
      )

      val expectedRequest = CreateAccountRequest(accountReference = ref)

      whenever(accountApi.createAccount(expectedRequest)).thenReturn(Mono.just(expectedResponse))

      val result = apiClient.createAccount(ref)

      assertThat(result).isEqualTo(expectedResponse)
      verify(accountApi).createAccount(expectedRequest)
    }

    @Test
    fun `should throw exception on 400 Bad Request (Duplicate)`() {
      val badRequestEx = WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "Bad Req", HttpHeaders(), ByteArray(0), null)
      whenever(accountApi.createAccount(any())).thenReturn(Mono.error(badRequestEx))

      assertThatThrownBy {
        apiClient.createAccount("A1234AA")
      }.isInstanceOf(WebClientResponseException::class.java)
    }

    @Test
    fun `should throw IllegalStateException if response body is null`() {
      whenever(accountApi.createAccount(any())).thenReturn(Mono.empty())

      assertThatThrownBy { apiClient.createAccount("A1234AA") }
        .isInstanceOf(IllegalStateException::class.java)
    }
  }

  @Nested
  inner class PostTransaction {
    @Test
    fun `should post transaction with idempotency key and return ID`() {
      val txId = UUID.randomUUID()
      val idempotencyKey = UUID.randomUUID()

      val request = CreateTransactionRequest(
        reference = "REF",
        description = "Desc",
        timestamp = Instant.now(),
        amount = 100L,
        postings = emptyList(),
      )

      val response = TransactionResponse(
        id = txId,
        reference = "REF",
        amount = 100L,
        createdBy = "user",
        createdAt = Instant.now(),
        description = "Desc",
        timestamp = Instant.now(),
        postings = emptyList(),
      )

      whenever(transactionApi.postTransaction(idempotencyKey, request))
        .thenReturn(Mono.just(response))

      val result = apiClient.postTransaction(request, idempotencyKey)

      assertThat(result).isEqualTo(txId)
      verify(transactionApi).postTransaction(idempotencyKey, request)
    }

    @Test
    fun `should throw IllegalStateException if response body is null`() {
      val idempotencyKey = UUID.randomUUID()
      val request = CreateTransactionRequest(
        reference = "REF",
        description = "Desc",
        timestamp = Instant.now(),
        amount = 100L,
        postings = emptyList(),
      )

      whenever(transactionApi.postTransaction(any(), any())).thenReturn(Mono.empty())

      assertThatThrownBy { apiClient.postTransaction(request, idempotencyKey) }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("returned null body")
    }
  }
}
