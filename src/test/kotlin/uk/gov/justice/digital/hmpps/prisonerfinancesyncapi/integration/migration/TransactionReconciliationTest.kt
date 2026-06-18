package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.migration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.GeneralLedgerTransactionMapping
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.GeneralLedgerTransactionMappingRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.PostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SearchPostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SearchTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.TimeConversionService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@TestPropertySource(
  properties = [
    "feature.general-ledger-api.enabled=true",
    "feature.general-ledger-api.test-prisoner-ids=A9971EC",
  ],
)
@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class TransactionReconciliationTest(
  @param:Autowired val transactionMappingRepository: GeneralLedgerTransactionMappingRepository,
) : IntegrationTestBase() {
  private val timeConversionService: TimeConversionService = TimeConversionService()

  @Transactional
  @BeforeEach
  fun setup() {
    integrationTestHelpers.clearDB()
    hmppsAuth.stubGrantToken()
  }

  @Nested
  inner class ReconcileOffenderTransactionById {
    @Test
    fun `should return a one to one general ledger transaction in Syscon format when given the corresponding ID`() {
      // ADV transaction LEI to Prisoner
      val legacyTransactionId = 12345L
      val prisonNumber = "A9971EC"
      val caseload = "LEI"
      val transactionType = "ADV"
      val caseloadSubAccountCode = 1021
      val glCaseloadAccountId: UUID = UUID.randomUUID()
      val glPrisonNumberAccountId: UUID = UUID.randomUUID()
      val glPrisonAdvAccountUUID: UUID = UUID.randomUUID()
      val glPrisonerCashAccountUUID: UUID = UUID.randomUUID()
      val transactionDate = Instant.now()
      val glTransactionId = UUID.randomUUID()
      val amount = 500L

      val mapping = GeneralLedgerTransactionMapping(
        legacyTransactionId = legacyTransactionId,
        entrySequence = 1,
        glTransactionUuid = glTransactionId,
        createdAt = transactionDate,
        transactionType = transactionType,
        caseloadId = caseload,
      )
      transactionMappingRepository.save(mapping)
      transactionMappingRepository.flush()

      val description = "Adv transaction LEI to Prisoner"
      generalLedgerApi.stubSearchTransactionsByUUIDs(
        listOf(glTransactionId),
        listOf(
          SearchTransactionResponse(
            id = glTransactionId,
            createdBy = "",
            createdAt = transactionDate,
            reference = "",
            description = description,
            timestamp = transactionDate,
            amount = amount,
            entrySequence = 1,
            postings = listOf(
              SearchPostingResponse(
                id = UUID.randomUUID(),
                createdBy = "",
                createdAt = transactionDate,
                type = SearchPostingResponse.Type.CR,
                amount = amount,
                subAccountID = glPrisonAdvAccountUUID,
                subAccountReference = "$caseloadSubAccountCode:$transactionType",
                accountID = glCaseloadAccountId,
                accountReference = caseload,
                entrySequence = 1,
                accountType = SearchPostingResponse.AccountType.PRISON,
              ),
              SearchPostingResponse(
                id = UUID.randomUUID(),
                createdBy = "",
                createdAt = transactionDate,
                type = SearchPostingResponse.Type.DR,
                amount = amount,
                subAccountID = glPrisonerCashAccountUUID,
                subAccountReference = "CASH",
                accountID = glPrisonNumberAccountId,
                accountReference = prisonNumber,
                entrySequence = 2,
                accountType = SearchPostingResponse.AccountType.PRISONER,
              ),
            ),
          ),
        ),
      )

      val transactionResponse = webTestClient
        .get()
        .uri("/reconcile/offender-transactions/$legacyTransactionId")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk
        .expectBody<SyncOffenderTransactionResponse>().returnResult().responseBody!!

      assertThat(transactionResponse.synchronizedTransactionId).isNull()
      assertThat(transactionResponse.legacyTransactionId).isEqualTo(legacyTransactionId)
      assertThat(transactionResponse.caseloadId).isEqualTo(caseload)
      assertThat(transactionResponse.transactionTimestamp).isEqualTo(timeConversionService.toLocalDateTime(transactionDate))
      assertThat(transactionResponse.createdAt).isEqualTo(timeConversionService.toLocalDateTime(transactionDate))
      assertThat(transactionResponse.lastModifiedAt).isNull()

      val transaction = transactionResponse.transactions[0]
      val expectedAmount = BigDecimal(amount).movePointLeft(2)

      assertThat(transaction.description).isEqualTo(description)
      assertThat(transaction.type).isEqualTo(transactionType)
      assertThat(transaction.amount).isEqualTo(expectedAmount)
      assertThat(transaction.reference).isEqualTo("")
      assertThat(transaction.generalLedgerEntries.size).isEqualTo(2)
      assertThat(transaction.offenderDisplayId).isEqualTo(prisonNumber)
      assertThat(transaction.subAccountType).isEqualTo("REG")
      assertThat(transaction.postingType).isEqualTo("DR")
      assertThat(transaction.generalLedgerEntries[0].entrySequence).isEqualTo(1)
      assertThat(transaction.generalLedgerEntries[0].code).isEqualTo(caseloadSubAccountCode)
      assertThat(transaction.generalLedgerEntries[0].postingType).isEqualTo("CR")
      assertThat(transaction.generalLedgerEntries[0].amount).isEqualTo(expectedAmount)

      assertThat(transaction.generalLedgerEntries[1].entrySequence).isEqualTo(2)
      assertThat(transaction.generalLedgerEntries[1].code).isEqualTo(2101)
      assertThat(transaction.generalLedgerEntries[1].postingType).isEqualTo("DR")
      assertThat(transaction.generalLedgerEntries[1].amount).isEqualTo(expectedAmount)
    }

    @Test
    fun `should return a one to many general ledger transaction in Syscon format when given the corresponding ID`() {
      // CANT transaction LEI to Prisoners
      val legacyTransactionId = 12345L
      val transactionDate = Instant.now()
      val amount = 500L

      val prisonerOne = "A9971EC"
      val glPrisonerOneAccountId: UUID = UUID.randomUUID()
      val glPrisonerOneAccountUUID: UUID = UUID.randomUUID()
      val glTransactionIdOne = UUID.randomUUID()

      val prisonerTwo = "B9971EC"
      val glPrisonerTwoAccountId: UUID = UUID.randomUUID()
      val glPrisonerTwoAccountUUID: UUID = UUID.randomUUID()
      val glTransactionIdTwo = UUID.randomUUID()

      val caseload = "LEI"
      val transactionType = "CANT"
      val caseloadSubAccountCode = 1021
      val glCaseloadAccountId: UUID = UUID.randomUUID()
      val glPrisonCanteenAccountUUID: UUID = UUID.randomUUID()

      val mappingOne = GeneralLedgerTransactionMapping(
        legacyTransactionId = legacyTransactionId,
        entrySequence = 1,
        glTransactionUuid = glTransactionIdOne,
        createdAt = transactionDate,
        transactionType = transactionType,
        caseloadId = caseload,
      )
      val mappingTwo = GeneralLedgerTransactionMapping(
        legacyTransactionId = legacyTransactionId,
        entrySequence = 2,
        glTransactionUuid = glTransactionIdTwo,
        createdAt = transactionDate,
        transactionType = transactionType,
        caseloadId = caseload,
      )

      transactionMappingRepository.saveAll(listOf(mappingOne, mappingTwo))
      transactionMappingRepository.flush()

      val description = "Adv transaction LEI to Prisoner"
      generalLedgerApi.stubSearchTransactionsByUUIDs(
        listOf(glTransactionIdOne, glTransactionIdTwo),
        listOf(
          SearchTransactionResponse(
            id = glTransactionIdOne,
            createdBy = "",
            createdAt = transactionDate,
            reference = "",
            description = description,
            timestamp = transactionDate,
            amount = amount,
            entrySequence = 1,
            postings = listOf(
              SearchPostingResponse(
                id = UUID.randomUUID(),
                createdBy = "",
                createdAt = transactionDate,
                type = SearchPostingResponse.Type.CR,
                amount = amount,
                subAccountID = glPrisonCanteenAccountUUID,
                subAccountReference = "$caseloadSubAccountCode:$transactionType",
                accountID = glCaseloadAccountId,
                accountReference = caseload,
                entrySequence = 1,
                accountType = SearchPostingResponse.AccountType.PRISON,
              ),
              SearchPostingResponse(
                id = UUID.randomUUID(),
                createdBy = "",
                createdAt = transactionDate,
                type = SearchPostingResponse.Type.DR,
                amount = amount,
                subAccountID = glPrisonerOneAccountUUID,
                subAccountReference = "CASH",
                accountID = glPrisonerOneAccountId,
                accountReference = prisonerOne,
                entrySequence = 2,
                accountType = SearchPostingResponse.AccountType.PRISONER,
              ),
            ),
          ),
          SearchTransactionResponse(
            id = glTransactionIdTwo,
            createdBy = "",
            createdAt = transactionDate,
            reference = "",
            description = description,
            timestamp = transactionDate,
            amount = amount,
            entrySequence = 1,
            postings = listOf(
              SearchPostingResponse(
                id = UUID.randomUUID(),
                createdBy = "",
                createdAt = transactionDate,
                type = SearchPostingResponse.Type.CR,
                amount = amount,
                subAccountID = glPrisonCanteenAccountUUID,
                subAccountReference = "$caseloadSubAccountCode:$transactionType",
                accountID = glCaseloadAccountId,
                accountReference = caseload,
                entrySequence = 3,
                accountType = SearchPostingResponse.AccountType.PRISON,
              ),
              SearchPostingResponse(
                id = UUID.randomUUID(),
                createdBy = "",
                createdAt = transactionDate,
                type = SearchPostingResponse.Type.DR,
                amount = amount,
                subAccountID = glPrisonerTwoAccountUUID,
                subAccountReference = "CASH",
                accountID = glPrisonerTwoAccountId,
                accountReference = prisonerTwo,
                entrySequence = 4,
                accountType = SearchPostingResponse.AccountType.PRISONER,
              ),
            ),
          ),
        ),
      )

      val transactionResponse = webTestClient
        .get()
        .uri("/reconcile/offender-transactions/$legacyTransactionId")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk
        .expectBody<SyncOffenderTransactionResponse>().returnResult().responseBody!!

      assertThat(transactionResponse.synchronizedTransactionId).isEqualTo(null)
      assertThat(transactionResponse.legacyTransactionId).isEqualTo(legacyTransactionId)
      assertThat(transactionResponse.caseloadId).isEqualTo(caseload)
      assertThat(transactionResponse.transactionTimestamp).isEqualTo(timeConversionService.toLocalDateTime(transactionDate))
      assertThat(transactionResponse.createdAt).isEqualTo(timeConversionService.toLocalDateTime(transactionDate))
      assertThat(transactionResponse.lastModifiedAt).isNull()

      val expectedAmount = BigDecimal(amount).movePointLeft(2)

      val transactionOne = transactionResponse.transactions[0]
      assertThat(transactionOne.description).isEqualTo(description)
      assertThat(transactionOne.type).isEqualTo(transactionType)
      assertThat(transactionOne.generalLedgerEntries.size).isEqualTo(2)
      assertThat(transactionOne.offenderDisplayId).isEqualTo(prisonerOne)
      assertThat(transactionOne.amount).isEqualTo(expectedAmount)
      assertThat(transactionOne.reference).isEqualTo("")
      assertThat(transactionOne.subAccountType).isEqualTo("REG")
      assertThat(transactionOne.postingType).isEqualTo("DR")

      assertThat(transactionOne.generalLedgerEntries[0].entrySequence).isEqualTo(1)
      assertThat(transactionOne.generalLedgerEntries[0].code).isEqualTo(caseloadSubAccountCode)
      assertThat(transactionOne.generalLedgerEntries[0].postingType).isEqualTo("CR")
      assertThat(transactionOne.generalLedgerEntries[0].amount).isEqualTo(expectedAmount)

      assertThat(transactionOne.generalLedgerEntries[1].entrySequence).isEqualTo(2)
      assertThat(transactionOne.generalLedgerEntries[1].code).isEqualTo(2101)
      assertThat(transactionOne.generalLedgerEntries[1].postingType).isEqualTo("DR")
      assertThat(transactionOne.generalLedgerEntries[1].amount).isEqualTo(expectedAmount)

      val transactionTwo = transactionResponse.transactions[1]
      assertThat(transactionTwo.description).isEqualTo(description)
      assertThat(transactionTwo.type).isEqualTo(transactionType)
      assertThat(transactionTwo.generalLedgerEntries.size).isEqualTo(2)
      assertThat(transactionTwo.offenderDisplayId).isEqualTo(prisonerTwo)
      assertThat(transactionTwo.amount).isEqualTo(expectedAmount)
      assertThat(transactionTwo.reference).isEqualTo("")
      assertThat(transactionOne.subAccountType).isEqualTo("REG")
      assertThat(transactionOne.postingType).isEqualTo("DR")

      assertThat(transactionTwo.generalLedgerEntries[0].entrySequence).isEqualTo(3)
      assertThat(transactionTwo.generalLedgerEntries[0].code).isEqualTo(caseloadSubAccountCode)
      assertThat(transactionTwo.generalLedgerEntries[0].postingType).isEqualTo("CR")
      assertThat(transactionTwo.generalLedgerEntries[0].amount).isEqualTo(expectedAmount)

      assertThat(transactionTwo.generalLedgerEntries[1].entrySequence).isEqualTo(4)
      assertThat(transactionTwo.generalLedgerEntries[1].code).isEqualTo(2101)
      assertThat(transactionTwo.generalLedgerEntries[1].postingType).isEqualTo("DR")
      assertThat(transactionTwo.generalLedgerEntries[1].amount).isEqualTo(expectedAmount)
    }

    @Test
    fun `should return a one to one prisoner sub account transaction in Syscon format when given the corresponding ID`() {
      // this is a special case where the second offenderTransaction doesn't have any generalLedgerEntries
      // CASH to Spends
      val legacyTransactionId = 12345L
      val prisonNumber = "A9971EC"
      val caseload = "LEI"
      val transactionType = "ATOF"
      val glPrisonNumberAccountId: UUID = UUID.randomUUID()
      val glPrisonerCashAccountUUID: UUID = UUID.randomUUID()
      val glPrisonerSpendsAccountUUID: UUID = UUID.randomUUID()
      val transactionDate = Instant.now()
      val glTransactionId = UUID.randomUUID()
      val amount = 500L

      // only one mapping because there is only one GL transaction
      val mapping = GeneralLedgerTransactionMapping(
        legacyTransactionId = legacyTransactionId,
        entrySequence = 1,
        glTransactionUuid = glTransactionId,
        createdAt = transactionDate,
        transactionType = transactionType,
        caseloadId = caseload,
      )
      transactionMappingRepository.save(mapping)
      transactionMappingRepository.flush()

      val description = "Adv transaction LEI to Prisoner"
      generalLedgerApi.stubSearchTransactionsByUUIDs(
        listOf(glTransactionId),
        listOf(
          SearchTransactionResponse(
            id = glTransactionId,
            createdBy = "",
            createdAt = transactionDate,
            reference = "",
            description = description,
            timestamp = transactionDate,
            amount = amount,
            entrySequence = 1,
            postings = listOf(
              SearchPostingResponse(
                id = UUID.randomUUID(),
                createdBy = "",
                createdAt = transactionDate,
                type = SearchPostingResponse.Type.CR,
                amount = amount,
                subAccountID = glPrisonerSpendsAccountUUID,
                subAccountReference = "SPENDS",
                accountID = glPrisonNumberAccountId,
                accountReference = prisonNumber,
                entrySequence = 1,
                accountType = SearchPostingResponse.AccountType.PRISONER,
              ),
              SearchPostingResponse(
                id = UUID.randomUUID(),
                createdBy = "",
                createdAt = transactionDate,
                type = SearchPostingResponse.Type.DR,
                amount = amount,
                subAccountID = glPrisonerCashAccountUUID,
                subAccountReference = "CASH",
                accountID = glPrisonNumberAccountId,
                accountReference = prisonNumber,
                entrySequence = 2,
                accountType = SearchPostingResponse.AccountType.PRISONER,
              ),
            ),
          ),
        ),
      )

      val transactionResponse = webTestClient
        .get()
        .uri("/reconcile/offender-transactions/$legacyTransactionId")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isOk
        .expectBody<SyncOffenderTransactionResponse>().returnResult().responseBody!!

      assertThat(transactionResponse.synchronizedTransactionId).isNull()
      assertThat(transactionResponse.legacyTransactionId).isEqualTo(legacyTransactionId)
      assertThat(transactionResponse.caseloadId).isEqualTo(caseload)
      assertThat(transactionResponse.transactionTimestamp).isEqualTo(timeConversionService.toLocalDateTime(transactionDate))
      assertThat(transactionResponse.createdAt).isEqualTo(timeConversionService.toLocalDateTime(transactionDate))
      assertThat(transactionResponse.lastModifiedAt).isNull()

      val expectedAmount = BigDecimal(amount).movePointLeft(2)

      val (transactionOne, transactionTwo) = transactionResponse.transactions

      assertThat(transactionOne.description).isEqualTo(description)
      assertThat(transactionOne.type).isEqualTo(transactionType)
      assertThat(transactionOne.amount).isEqualTo(expectedAmount)
      assertThat(transactionOne.reference).isEqualTo("")
      assertThat(transactionOne.generalLedgerEntries.size).isEqualTo(2)
      assertThat(transactionOne.offenderDisplayId).isEqualTo(prisonNumber)
      assertThat(transactionOne.subAccountType).isEqualTo("SPND")
      assertThat(transactionOne.postingType).isEqualTo("CR")
      assertThat(transactionOne.generalLedgerEntries[0].entrySequence).isEqualTo(1)
      assertThat(transactionOne.generalLedgerEntries[0].code).isEqualTo(2102)
      assertThat(transactionOne.generalLedgerEntries[0].postingType).isEqualTo("CR")
      assertThat(transactionOne.generalLedgerEntries[0].amount).isEqualTo(expectedAmount)

      assertThat(transactionOne.generalLedgerEntries[1].entrySequence).isEqualTo(2)
      assertThat(transactionOne.generalLedgerEntries[1].code).isEqualTo(2101)
      assertThat(transactionOne.generalLedgerEntries[1].postingType).isEqualTo("DR")
      assertThat(transactionOne.generalLedgerEntries[1].amount).isEqualTo(expectedAmount)

      assertThat(transactionTwo.description).isEqualTo(description)
      assertThat(transactionTwo.type).isEqualTo(transactionType)
      assertThat(transactionTwo.amount).isEqualTo(expectedAmount)
      assertThat(transactionTwo.reference).isEqualTo("")
      assertThat(transactionTwo.offenderDisplayId).isEqualTo(prisonNumber)
      assertThat(transactionTwo.subAccountType).isEqualTo("REG")
      assertThat(transactionTwo.postingType).isEqualTo("DR")
      assertThat(transactionTwo.generalLedgerEntries).hasSize(0)
    }

    @Test
    fun `should return 404 when the transaction ID is not found in GL`() {
      val legacyTransactionId = 12345L

      val prisonNumber = "A9971EC"
      val prisonerAccountUUID: UUID = UUID.randomUUID()
      val creditSubAccountUUID: UUID = UUID.randomUUID()
      val debtorSubAccountUUID: UUID = UUID.randomUUID()

      generalLedgerApi.stubGetAccount(
        reference = prisonNumber,
        subAccounts = listOf(
          SubAccountResponse(
            id = debtorSubAccountUUID,
            reference = "CASH",
            parentAccountId = prisonerAccountUUID,
            createdBy = "TEST",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = creditSubAccountUUID,
            reference = "SPENDS",
            parentAccountId = prisonerAccountUUID,
            createdBy = "TEST",
            createdAt = Instant.now(),
          ),
        ),
      )

      val transactionPostings = listOf(
        PostingResponse(
          id = prisonerAccountUUID,
          createdBy = "TEST",
          createdAt = Instant.now(),
          type = PostingResponse.Type.CR,
          amount = 6,
          subAccountID = creditSubAccountUUID,
        ),
        PostingResponse(
          id = prisonerAccountUUID,
          createdBy = "TEST",
          createdAt = Instant.now(),
          type = PostingResponse.Type.DR,
          amount = 6,
          subAccountID = debtorSubAccountUUID,
        ),
      )

      val returnGeneralLedgerUUID = UUID.randomUUID()

      val transactionResponse = generalLedgerApi.stubPostTransaction(
        creditorSubAccountUuid = creditSubAccountUUID.toString(),
        debtorSubAccountUuid = debtorSubAccountUUID.toString(),
        reference = "REF",
        returnUUID = returnGeneralLedgerUUID,
        postings = transactionPostings,
        amount = 6,
      )

      val generalLedgerEntries = listOf(
        GeneralLedgerEntry(
          entrySequence = 1,
          code = 2101,
          postingType = "DR",
          amount = BigDecimal.valueOf(500),
        ),
        GeneralLedgerEntry(
          entrySequence = 2,
          code = 2102,
          postingType = "CR",
          amount = BigDecimal.valueOf(500),
        ),
      )

      val offenderTransaction = integrationTestHelpers.createOffenderTransaction(
        entrySequence = 1,
        offenderId = 1,
        offenderDisplayId = prisonNumber,
        offenderBookingId = 1,
        subAccountType = "",
        amount = BigDecimal.valueOf(5.00),
        generalLedgerEntries = generalLedgerEntries,
        reference = "ANY_REF",
      )

      integrationTestHelpers.syncOffenderTransactions(
        transactionId = legacyTransactionId,
        caseloadId = "LEI",
        transactionTimestamp = LocalDateTime.now(),
        createdAt = LocalDateTime.now(),
        offenderTransactions = listOf(offenderTransaction),
      )

      generalLedgerApi.stubSearchTransactionsByUUIDs(
        emptyList(),
        emptyList(),
      )

      val error = webTestClient
        .get()
        .uri("/reconcile/offender-transactions/$legacyTransactionId")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isNotFound
        .expectBody<ErrorResponse>().returnResult().responseBody!!

      assertThat(error.developerMessage).isEqualTo("No gl transaction found for gl $legacyTransactionId")
    }

    @Test
    fun `should return a 404 when there is no mapping entry found in sync`() {
      val incorrectId = 123
      val error = webTestClient
        .get()
        .uri("/reconcile/offender-transactions/$incorrectId")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().isNotFound
        .expectBody<ErrorResponse>().returnResult().responseBody!!

      assertThat(error.developerMessage).isEqualTo("No mapping found for $incorrectId")
    }

    @Test
    fun `should return 401 when unauthorized`() {
      val incorrectUUID = UUID.randomUUID()
      webTestClient.get()
        .uri("/reconcile/offender-transactions/$incorrectUUID")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 403 when given an incorrect role`() {
      val incorrectId = 123
      webTestClient.get()
        .uri("/reconcile/offender-transactions/$incorrectId")
        .headers(setAuthorisation(roles = listOf("INCORRECT_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return 502 when the general ledger API returns a 5XX error`() {
      val legacyTransactionId = 12345L

      val mapping = GeneralLedgerTransactionMapping(
        legacyTransactionId = legacyTransactionId,
        entrySequence = 1,
        glTransactionUuid = UUID.randomUUID(),
        createdAt = Instant.now(),
        transactionType = "ATOF",
        caseloadId = "LEI",
      )
      transactionMappingRepository.save(mapping)
      transactionMappingRepository.flush()

      generalLedgerApi.stubSearchTransactionsByUUIDsThrows500()

      val error = webTestClient
        .get()
        .uri("/reconcile/offender-transactions/$legacyTransactionId")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
        .exchange()
        .expectStatus().is5xxServerError
        .expectBody<ErrorResponse>().returnResult().responseBody!!

      assertThat(error.status).isEqualTo(502)
    }
  }
}
