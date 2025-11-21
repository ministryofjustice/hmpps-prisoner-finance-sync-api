package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "transaction_type")
data class TransactionType(
  @Id
  @Column(name = "txn_type", nullable = false)
  val txnType: String,

  @Column(name = "description", nullable = false)
  val description: String,

  @Column(name = "active_flag", nullable = false)
  val activeFlag: String,

  @Column(name = "txn_usage", nullable = false)
  val txnUsage: String,

  @Column(name = "all_caseload_flag", nullable = false)
  val allCaseloadFlag: String,

  @Column(name = "expiry_date", nullable = false)
  val expiryDate: String,

  @Column(name = "update_allowed_flag", nullable = false)
  val updateAllowedFlag: String,

  @Column(name = "manual_invoice_flag", nullable = false)
  val manualInvoiceFlag: String,

  @Column(name = "credit_obligation_type", nullable = false)
  val creditObligationType: String,

  @Column(name = "list_seq")
  val listSeq: Int? = null,

  @Column(name = "gross_net_flag", nullable = false)
  val grossNetFlag: String,

  @Column(name = "caseload_type", nullable = false)
  val caseloadType: String,
)
