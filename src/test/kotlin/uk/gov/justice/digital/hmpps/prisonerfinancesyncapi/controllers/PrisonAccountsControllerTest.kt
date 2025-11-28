package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.controllers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.PrisonAccountDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.PrisonAccountDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.TransactionDetails
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.TransactionDetailsList
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger.LedgerQueryService
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
@DisplayName("PrisonAccountsController")
class PrisonAccountsControllerTest {

  @Mock
  private lateinit var ledgerQueryService: LedgerQueryService

  @InjectMocks
  private lateinit var prisonAccountsController: PrisonAccountsController

  private fun createPrisonAccountDetailsList() = PrisonAccountDetailsList(
    items = listOf(
      createPrisonAccountDetails(),
    ),
  )

  private fun createPrisonAccountDetails() = PrisonAccountDetails(
    code = 1000,
    name = "Cash",
    classification = "Asset",
    postingType = "CR",
    balance = BigDecimal(0),
    prisonId = "MDI",
  )

  @Nested
  @DisplayName("getPrisonAccountTransaction")
  inner class GetPrisonAccountTransactionTests {

    private fun createTransactionDetailsList() = TransactionDetailsList(
      items = listOf(
        TransactionDetails(
          id = "111222333",
          date = "2024-01-01",
          type = "CREDIT",
          description = "Some description",
          reference = "ABC123",
          clientRequestId = null,
          postings = listOf(
            TransactionDetails.TransactionPosting(
              account = TransactionDetails.TransactionAccountDetails(
                code = 1111,
                name = "Some Account Name",
                transactionType = "CREDIT",
                transactionDescription = "Desc",
                prison = "MDI",
                prisoner = "12345",
                classification = "General",
                postingType = PostingType.CR,
              ),
              address = "somewhere",
              postingType = PostingType.CR,
              amount = BigDecimal(10),
            ),
          ),
        ),
      ),
    )

    @Test
    fun `should return OK when TransactionDetailsList is returned`() {
      val prisonId = "MDI"
      val accountCode = 1111
      val transactionId = "111222333"

      `when`(ledgerQueryService.getPrisonAccountTransaction("MDI", 1111, transactionId)).thenReturn(
        listOf(
          TransactionDetails(
            id = transactionId,
            date = "2024-01-01",
            type = "CREDIT",
            description = "Some description",
            reference = "ABC123",
            clientRequestId = null,
            postings = listOf(
              TransactionDetails.TransactionPosting(
                account = TransactionDetails.TransactionAccountDetails(
                  code = accountCode,
                  name = "Some Account Name",
                  transactionType = "CREDIT",
                  transactionDescription = "Desc",
                  prison = prisonId,
                  prisoner = "12345",
                  classification = "General",
                  postingType = PostingType.CR,
                ),
                address = "somewhere",
                postingType = PostingType.CR,
                amount = BigDecimal(10),
              ),
            ),
          ),
        ),
      )

      val response = prisonAccountsController.getPrisonAccountTransaction(prisonId, accountCode, transactionId)

      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(createTransactionDetailsList())
    }

    @Test
    fun `should throw NoResourceFoundException when TransactionDetailsList is empty`() {
      val prisonId = "MDI"
      val accountCode = 1111
      val transactionId = "111222333"

      `when`(ledgerQueryService.getPrisonAccountTransaction("MDI", 1111, transactionId)).thenReturn(
        listOf(),
      )

      assertThrows<NoResourceFoundException> {
        prisonAccountsController.getPrisonAccountTransaction(prisonId, accountCode, transactionId)
      }
    }
  }

  @Nested
  @DisplayName("getPrisonAccountTransactions")
  inner class GetPrisonAccountTransactionsTests {

    private fun createTransactionDetails() = TransactionDetailsList(
      items = listOf(
        TransactionDetails(
          id = "111222333",
          date = "2024-01-01",
          type = "CREDIT",
          description = "Some description",
          reference = "ABC123",
          clientRequestId = null,
          postings = listOf(
            TransactionDetails.TransactionPosting(
              account = TransactionDetails.TransactionAccountDetails(
                code = 1111,
                name = "Some Account Name",
                transactionType = "CREDIT",
                transactionDescription = "Desc",
                prison = "MDI",
                prisoner = "12345",
                classification = "General",
                postingType = PostingType.CR,
              ),
              address = "somewhere",
              postingType = PostingType.CR,
              amount = BigDecimal(10),
            ),
          ),
        ),
      ),
    )

    @Test
    fun `should return OK when listOf TransactionDetails is returned`() {
      val prisonId = "MDI"
      val accountCode = 1111
      val date = LocalDate.now()

      `when`(ledgerQueryService.listPrisonAccountTransactions("MDI", 1111, date)).thenReturn(
        listOf(
          TransactionDetails(
            id = "111222333",
            date = "2024-01-01",
            type = "CREDIT",
            description = "Some description",
            reference = "ABC123",
            clientRequestId = null,
            postings = listOf(
              TransactionDetails.TransactionPosting(
                account = TransactionDetails.TransactionAccountDetails(
                  code = accountCode,
                  name = "Some Account Name",
                  transactionType = "CREDIT",
                  transactionDescription = "Desc",
                  prison = prisonId,
                  prisoner = "12345",
                  classification = "General",
                  postingType = PostingType.CR,
                ),
                address = "somewhere",
                postingType = PostingType.CR,
                amount = BigDecimal(10),
              ),
            ),
          ),
        ),
      )

      val response = prisonAccountsController.getPrisonAccountTransactions(prisonId, accountCode, date)

      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(createTransactionDetails())
    }

    @Test
    fun `should throw NoResourceFoundException when listOf TransactionDetails is empty`() {
      val prisonId = "MDI"
      val accountCode = 1111
      val date = LocalDate.now()

      `when`(ledgerQueryService.listPrisonAccountTransactions("MDI", 1111, date)).thenReturn(
        listOf(),
      )

      assertThrows<NoResourceFoundException> {
        prisonAccountsController.getPrisonAccountTransactions(prisonId, accountCode, date)
      }
    }
  }

  @Nested
  @DisplayName("listPrisonAccounts")
  inner class ListPrisonAccountsTests {

    @Test
    fun `should return OK when PrisonAccountDetailsList is returned`() {
      val prisonId = "MDI"

      `when`(ledgerQueryService.listPrisonAccountDetails("MDI")).thenReturn(
        listOf(
          PrisonAccountDetails(
            code = 1000,
            name = "Cash",
            classification = "Asset",
            postingType = "CR",
            balance = BigDecimal(0),
            prisonId = "MDI",
          ),
        ),
      )

      val response = prisonAccountsController.listPrisonAccounts(prisonId)

      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(createPrisonAccountDetailsList())
    }
  }

  @Nested
  @DisplayName("getPrisonAccountDetails")
  inner class GetPrisonAccountDetailsTests {

    @Test
    fun `should return OK when PrisonAccountDetails is returned`() {
      val prisonId = "MDI"
      val accountCode = 1111

      `when`(ledgerQueryService.getPrisonAccountDetails("MDI", 1111)).thenReturn(
        PrisonAccountDetails(
          code = 1000,
          name = "Cash",
          classification = "Asset",
          postingType = "CR",
          balance = BigDecimal(0),
          prisonId = "MDI",
        ),
      )

      val response = prisonAccountsController.getPrisonAccountDetails(prisonId, accountCode)

      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body).isEqualTo(createPrisonAccountDetails())
    }

    @Test
    fun `should throw NoResourceFoundException when PrisonAccountDetails is null`() {
      val prisonId = "MDI"
      val accountCode = 1111

      `when`(ledgerQueryService.getPrisonAccountDetails(prisonId, accountCode))
        .thenReturn(null)

      assertThrows<NoResourceFoundException> {
        prisonAccountsController.getPrisonAccountDetails(prisonId, accountCode)
      }
    }
  }
}
