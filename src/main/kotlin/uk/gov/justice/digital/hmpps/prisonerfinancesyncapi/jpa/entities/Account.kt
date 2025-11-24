package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "account")
data class Account(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(name = "uuid", nullable = false)
  val uuid: UUID = UUID.randomUUID(),

  @Column(name = "prison_id", nullable = true)
  val prisonId: Long?,

  @Column(name = "name", nullable = false)
  val name: String,

  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false)
  val accountType: AccountType,

  @Column(name = "account_code", nullable = false)
  val accountCode: Int,

  @Column(name = "prison_number")
  val prisonNumber: String? = null,

  @Column(name = "sub_account_type")
  val subAccountType: String? = null,

  @Enumerated(EnumType.STRING)
  @Column(name = "posting_type", nullable = false)
  var postingType: PostingType,
)
