package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.reports

import java.math.BigDecimal
import java.time.LocalDate

data class SummaryOfPaymentAndReceiptsReport(
  val postings: List<PostingReportEntry>,
) {
  data class PostingReportEntry(
    val date: LocalDate,
    val businessDate: LocalDate,
    val transactionUsage: String? = null,
    val type: String,
    val description: String,
    var private: BigDecimal,
    var spending: BigDecimal,
    var saving: BigDecimal,
    var credits: BigDecimal,
    var debits: BigDecimal,
    var total: BigDecimal,
  )
}
