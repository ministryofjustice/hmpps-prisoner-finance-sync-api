package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "transaction_entry")
data class TransactionEntry(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(name = "transaction_id", nullable = false)
  val transactionId: Long,

  @Column(name = "account_id", nullable = false)
  val accountId: Long,

  @Column(name = "amount", nullable = false)
  val amount: BigDecimal,

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false)
  val entryType: PostingType,
)
