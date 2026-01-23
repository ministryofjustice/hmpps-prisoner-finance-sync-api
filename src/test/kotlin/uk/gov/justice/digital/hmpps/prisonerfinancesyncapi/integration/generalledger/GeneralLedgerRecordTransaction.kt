package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.generalledger

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.config.ROLE_PRISONER_FINANCE_SYNC
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

@TestPropertySource(properties = ["feature.general-ledger-api.enabled=true"])
@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class GeneralLedgerRecordTransaction : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun setup() {
    generalLedgerApi.resetAll()
    hmppsAuth.stubGrantToken()
  }

  @Test
  fun `should record 'Advance' transaction to general ledger`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val offenderNo = "A_PRISONER"
    val amount = BigDecimal("5.00")

    val prisonerParentRef = offenderNo
    val prisonerSubRef = "SPND"

    val prisonParentRef = prisonId
    // Service Logic: "Code:Type" -> "1502:ADV"
    val prisonSubRef = "1502:ADV"

    val prisonerParentUuid = UUID.randomUUID().toString()
    val prisonerSubUuid = UUID.randomUUID().toString()
    val prisonParentUuid = UUID.randomUUID().toString()
    val prisonSubUuid = UUID.randomUUID().toString()

    generalLedgerApi.stubGetAccountNotFound(prisonerParentRef)
    generalLedgerApi.stubCreateAccount(prisonerParentRef, prisonerParentUuid)
    generalLedgerApi.stubGetSubAccountNotFound(prisonerParentRef, prisonerSubRef)
    generalLedgerApi.stubCreateSubAccount(prisonerParentUuid, prisonerSubRef, prisonerSubUuid)

    generalLedgerApi.stubGetAccountNotFound(prisonParentRef)
    generalLedgerApi.stubCreateAccount(prisonParentRef, prisonParentUuid)
    generalLedgerApi.stubGetSubAccountNotFound(prisonParentRef, prisonSubRef)
    generalLedgerApi.stubCreateSubAccount(prisonParentUuid, prisonSubRef, prisonSubUuid)

    generalLedgerApi.stubPostTransaction(
      creditorSubAccountUuid = prisonerSubUuid, // 2102 CR
      debtorSubAccountUuid = prisonSubUuid, // 1502 DR
    )

    val transactionId = Random.nextLong(10000, 99999)
    val timestamp = LocalDateTime.now()

    val request = SyncOffenderTransactionRequest(
      transactionId = transactionId,
      caseloadId = prisonId,
      transactionTimestamp = timestamp,
      createdAt = timestamp.plusSeconds(5),
      createdBy = "OMS_OWNER",
      requestId = UUID.randomUUID(),
      createdByDisplayName = "OMS_OWNER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 5306470,
          offenderDisplayId = offenderNo,
          offenderBookingId = 2970777,
          subAccountType = "SPND",
          postingType = "CR",
          type = "ADV",
          description = "Test Transaction for Balance Check",
          amount = amount.toDouble(),
          reference = "REF-$transactionId",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 2102, "CR", amount.toDouble()),
            GeneralLedgerEntry(2, 1502, "DR", amount.toDouble()),
          ),
        ),
      ),
    )

    webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isCreated
      .expectBody()
      .jsonPath("$.action").isEqualTo("CREATED")

    generalLedgerApi.verifyCreateAccount(prisonerParentRef)
    generalLedgerApi.verifyCreateSubAccount(prisonerParentUuid, prisonerSubRef)

    generalLedgerApi.verifyCreateAccount(prisonParentRef)
    generalLedgerApi.verifyCreateSubAccount(prisonParentUuid, prisonSubRef)
  }

  @Test
  fun `should record multi-prisoner 'Canteen' transaction to general ledger`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()

    val canteenSubRef = "2501:CANT"
    val spendsSubRef = "SPND"

    val prisoner1 = "PRISONER_1"
    val amount1 = BigDecimal("1.40")

    val prisoner2 = "PRISONER_2"
    val amount2 = BigDecimal("2.20")

    val prisoner1ParentUuid = UUID.randomUUID().toString()
    val prisoner1SubUuid = UUID.randomUUID().toString()
    val prisoner2ParentUuid = UUID.randomUUID().toString()
    val prisoner2SubUuid = UUID.randomUUID().toString()
    val prisonParentUuid = UUID.randomUUID().toString()
    val canteenSubUuid = UUID.randomUUID().toString()

    generalLedgerApi.stubGetAccountNotFound(prisoner1)
    generalLedgerApi.stubCreateAccount(prisoner1, prisoner1ParentUuid)
    generalLedgerApi.stubGetSubAccountNotFound(prisoner1, spendsSubRef)
    generalLedgerApi.stubCreateSubAccount(prisoner1ParentUuid, spendsSubRef, prisoner1SubUuid)

    generalLedgerApi.stubGetAccountNotFound(prisoner2)
    generalLedgerApi.stubCreateAccount(prisoner2, prisoner2ParentUuid)
    generalLedgerApi.stubGetSubAccountNotFound(prisoner2, spendsSubRef)
    generalLedgerApi.stubCreateSubAccount(prisoner2ParentUuid, spendsSubRef, prisoner2SubUuid)

    generalLedgerApi.stubGetAccountNotFound(prisonId)
    generalLedgerApi.stubCreateAccount(prisonId, prisonParentUuid)
    generalLedgerApi.stubGetSubAccountNotFound(prisonId, canteenSubRef)
    generalLedgerApi.stubCreateSubAccount(prisonParentUuid, canteenSubRef, canteenSubUuid)

    generalLedgerApi.stubPostTransaction(debtorSubAccountUuid = prisoner1SubUuid, creditorSubAccountUuid = canteenSubUuid)
    generalLedgerApi.stubPostTransaction(debtorSubAccountUuid = prisoner2SubUuid, creditorSubAccountUuid = canteenSubUuid)

    val transactionId = Random.nextLong(10000, 99999)
    val timestamp = LocalDateTime.now()

    val request = SyncOffenderTransactionRequest(
      transactionId = transactionId,
      requestId = UUID.randomUUID(),
      caseloadId = prisonId,
      transactionTimestamp = timestamp,
      createdAt = timestamp.plusSeconds(5),
      createdBy = "OMS_OWNER",
      createdByDisplayName = "Jeffrey",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 2605754,
          offenderDisplayId = prisoner1,
          offenderBookingId = 1223356,
          subAccountType = "SPND",
          postingType = "DR",
          type = "CANT",
          description = "Canteen Spend",
          amount = amount1.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 2102, "DR", amount1.toDouble()),
            GeneralLedgerEntry(2, 2501, "CR", amount1.toDouble()),
          ),
        ),
        OffenderTransaction(
          entrySequence = 2,
          offenderId = 4305755,
          offenderDisplayId = prisoner2,
          offenderBookingId = 789567,
          subAccountType = "SPND",
          postingType = "DR",
          type = "CANT",
          description = "Canteen Spend",
          amount = amount2.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 2102, "DR", amount2.toDouble()),
            GeneralLedgerEntry(2, 2501, "CR", amount2.toDouble()),
          ),
        ),
      ),
    )

    webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isCreated

    generalLedgerApi.verifyCreateAccount(prisoner1)
    generalLedgerApi.verifyCreateAccount(prisoner2)
    generalLedgerApi.verifyCreateAccount(prisonId)
  }

  @Test
  fun `should record 'Sub-Account Transfer' transaction to general ledger`() {
    val prisonId = "LEI"
    val offenderNo = "A1234AA"
    val amount = BigDecimal("12.00")

    val spendsSubRef = "SPND"
    val cashSubRef = "CASH"

    val prisonerParentUuid = UUID.randomUUID().toString()
    val spendsSubUuid = UUID.randomUUID().toString()
    val cashSubUuid = UUID.randomUUID().toString()

    generalLedgerApi.stubGetAccountNotFound(offenderNo)
    generalLedgerApi.stubCreateAccount(offenderNo, prisonerParentUuid)

    generalLedgerApi.stubGetSubAccountNotFound(offenderNo, spendsSubRef)
    generalLedgerApi.stubCreateSubAccount(prisonerParentUuid, spendsSubRef, spendsSubUuid)

    generalLedgerApi.stubGetSubAccountNotFound(offenderNo, cashSubRef)
    generalLedgerApi.stubCreateSubAccount(prisonerParentUuid, cashSubRef, cashSubUuid)

    generalLedgerApi.stubPostTransaction(
      debtorSubAccountUuid = spendsSubUuid, // DR Spends
      creditorSubAccountUuid = cashSubUuid, // CR Cash
    )

    val transactionId = Random.nextLong(10000, 99999)
    val timestamp = LocalDateTime.now()

    val request = SyncOffenderTransactionRequest(
      transactionId = transactionId,
      requestId = UUID.fromString("82f6a7bf-bae2-44ed-8573-46c84c41dc3e"),
      caseloadId = prisonId,
      transactionTimestamp = timestamp,
      createdAt = timestamp,
      createdBy = "OMS_OWNER",
      createdByDisplayName = "OMS_OWNER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 2607103,
          offenderDisplayId = offenderNo,
          offenderBookingId = 1227181,
          subAccountType = "SPND",
          postingType = "DR",
          type = "OT",
          description = "Sub-Account Transfer",
          amount = amount.toDouble(),
          reference = null,
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(1, 2102, "DR", amount.toDouble()),
            GeneralLedgerEntry(2, 2101, "CR", amount.toDouble()),
          ),
        ),
      ),
    )

    webTestClient.post()
      .uri("/sync/offender-transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE_SYNC)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isCreated

    generalLedgerApi.verifyCreateAccount(offenderNo)
    generalLedgerApi.verifyCreateSubAccount(prisonerParentUuid, spendsSubRef)
    generalLedgerApi.verifyCreateSubAccount(prisonerParentUuid, cashSubRef)
  }
}
