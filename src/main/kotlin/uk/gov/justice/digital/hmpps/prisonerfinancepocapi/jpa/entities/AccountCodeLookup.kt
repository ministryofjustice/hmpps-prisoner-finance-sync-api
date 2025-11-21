package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "account_code_lookup")
data class AccountCodeLookup(
  @Id
  @Column(name = "account_code", nullable = false)
  val accountCode: Int,

  @Column(name = "name", nullable = false)
  val name: String,

  @Column(name = "classification", nullable = false)
  val classification: String,

  @Enumerated(EnumType.STRING)
  @Column(name = "posting_type", nullable = false)
  val postingType: PostingType,

  @Column(name = "parent_account_code")
  val parentAccountCode: Int? = null,
)
