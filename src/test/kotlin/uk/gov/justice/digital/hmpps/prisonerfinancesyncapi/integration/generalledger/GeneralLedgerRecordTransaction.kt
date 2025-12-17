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
    val prisonId = "PRISON_1"
    val offenderNo = "A_PRISONER"
    val amount = BigDecimal("5.00")

    val receivableForAdvancesAccountCode = 1502
    val nominalRef = "$prisonId-$receivableForAdvancesAccountCode"

    val prisonerAccountId = UUID.randomUUID().toString()
    val receivableForAdvancesId = UUID.randomUUID().toString()

    // 1. Stub Account Creation for Prisoner
    generalLedgerApi.stubGetAccountNotFound(offenderNo)
    generalLedgerApi.stubCreateAccount(offenderNo, prisonerAccountId)

    // 2. Stub Account Creation for GL Account
    generalLedgerApi.stubGetAccountNotFound(nominalRef)
    generalLedgerApi.stubCreateAccount(nominalRef, receivableForAdvancesId)

    // 3. Stub the Transaction Post
    // Expect: Creditor = Prisoner (Receiving money), Debtor = GL Account (Paying money)
    generalLedgerApi.stubPostTransaction(
      creditorUuid = prisonerAccountId,
      debtorUuid = receivableForAdvancesId,
    )

    val request = createSyncRequest(
      caseloadId = prisonId,
      timestamp = LocalDateTime.now(),
      offenderDisplayId = offenderNo,
      offenderAccountCode = 2102,
      offenderSubAccountType = "SPND",
      offenderPostingType = "CR",
      amount = amount,
      transactionType = "ADV",
      glCode = receivableForAdvancesAccountCode,
      glPostingType = "DR",
      description = "Advance",
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
    generalLedgerApi.verifyCreateAccount(nominalRef)
  }

  @Suppress("LongParameterList")
  private fun createSyncRequest(
    caseloadId: String,
    timestamp: LocalDateTime,
    offenderDisplayId: String,
    offenderAccountCode: Int,
    offenderSubAccountType: String,
    offenderPostingType: String,
    amount: BigDecimal,
    transactionType: String,
    glCode: Int,
    glPostingType: String,
    description: String,
  ): SyncOffenderTransactionRequest {
    val transactionId = Random.nextLong(10000, 99999)
    val requestId = UUID.randomUUID()

    // The Prisoner's perspective of the entry
    val offenderEntry = GeneralLedgerEntry(
      entrySequence = 1,
      code = offenderAccountCode,
      postingType = offenderPostingType,
      amount = amount.toDouble(),
    )

    // The Contra entry (Balancing side)
    val glEntry = GeneralLedgerEntry(
      entrySequence = 2,
      code = glCode,
      postingType = glPostingType,
      amount = amount.toDouble(),
    )

    return SyncOffenderTransactionRequest(
      transactionId = transactionId,
      caseloadId = caseloadId,
      transactionTimestamp = timestamp,
      createdAt = timestamp.plusSeconds(1),
      createdBy = "OMS_OWNER",
      offenderTransactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 5306470,
          offenderDisplayId = offenderDisplayId,
          offenderBookingId = 2970777,
          subAccountType = offenderSubAccountType,
          postingType = offenderPostingType,
          type = transactionType,
          description = description,
          amount = amount.toDouble(),
          reference = "REF-$transactionId",
          generalLedgerEntries = listOf(offenderEntry, glEntry),
        ),
      ),
      requestId = requestId,
      createdByDisplayName = "OMS_OWNER",
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
    )
  }
}
