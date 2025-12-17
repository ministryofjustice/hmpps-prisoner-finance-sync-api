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
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.sync.SyncOffenderTransactionRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

@TestPropertySource(properties = ["feature.general-ledger-api.enabled=true"])
@ExtendWith(GeneralLedgerApiExtension::class)
class GeneralLedgerRecordTransaction : IntegrationTestBase() {

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun setup() {
    generalLedgerApi.resetAll()
  }

  @Test
  fun `should record 'Advance' transaction to general ledger`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val offenderNo = "A_PRISONER"
    val amount = BigDecimal("5.00")

    val advancesGlCode = 1502
    val glAccountRef = "$prisonId-$advancesGlCode"

    val prisonerAccountId = UUID.randomUUID().toString()
    val receivableForAdvancesId = UUID.randomUUID().toString()

    generalLedgerApi.stubGetAccountNotFound(offenderNo)
    generalLedgerApi.stubCreateAccount(offenderNo, prisonerAccountId)

    generalLedgerApi.stubGetAccountNotFound(glAccountRef)
    generalLedgerApi.stubCreateAccount(glAccountRef, receivableForAdvancesId)

    generalLedgerApi.stubPostTransaction(
      creditorUuid = prisonerAccountId,
      debtorUuid = receivableForAdvancesId,
    )

    val transactionId = Random.nextLong(10000, 99999)
    val timestamp = LocalDateTime.now()

    val request = SyncOffenderTransactionRequest(
      transactionId = Random.nextLong(10000, 99999),
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
            GeneralLedgerEntry(
              entrySequence = 1,
              code = 2102,
              postingType = "CR",
              amount = amount.toDouble(),
            ),
            GeneralLedgerEntry(
              entrySequence = 2,
              code = advancesGlCode,
              postingType = "DR",
              amount = amount.toDouble(),
            ),
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

    generalLedgerApi.verifyCreateAccount(offenderNo)
    generalLedgerApi.verifyCreateAccount(glAccountRef)
  }

  @Test
  fun `should record multi-prisoner 'Canteen' transaction to general ledger`() {
    val prisonId = UUID.randomUUID().toString().substring(0, 3).uppercase()
    val canteenGlCode = 2501
    val canteenGlRef = "$prisonId-$canteenGlCode"

    val prisoner1 = "PRISONER_1"
    val amount1 = BigDecimal("1.40")

    val prisoner2 = "PRISONER_2"
    val amount2 = BigDecimal("2.20")

    val prisoner1Uuid = UUID.randomUUID().toString()
    val prisoner2Uuid = UUID.randomUUID().toString()
    val glAccountUuid = UUID.randomUUID().toString()

    // Prisoner 1
    generalLedgerApi.stubGetAccountNotFound(prisoner1)
    generalLedgerApi.stubCreateAccount(prisoner1, prisoner1Uuid)

    // Prisoner 2
    generalLedgerApi.stubGetAccountNotFound(prisoner2)
    generalLedgerApi.stubCreateAccount(prisoner2, prisoner2Uuid)

    // GL Account (Shared by both transactions)
    generalLedgerApi.stubGetAccountNotFound(canteenGlRef)
    generalLedgerApi.stubCreateAccount(canteenGlRef, glAccountUuid)

    generalLedgerApi.stubPostTransaction(
      debtorUuid = prisoner1Uuid,
      creditorUuid = glAccountUuid,
    )

    generalLedgerApi.stubPostTransaction(
      debtorUuid = prisoner2Uuid,
      creditorUuid = glAccountUuid,
    )

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
            GeneralLedgerEntry(2, canteenGlCode, "CR", amount1.toDouble()), // Contra is CR
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
            GeneralLedgerEntry(2, canteenGlCode, "CR", amount2.toDouble()), // Contra is CR
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

    generalLedgerApi.verifyCreateAccount(prisoner1)
    generalLedgerApi.verifyCreateAccount(prisoner2)
    generalLedgerApi.verifyCreateAccount(canteenGlRef)
  }
}
