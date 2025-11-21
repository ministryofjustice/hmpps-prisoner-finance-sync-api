package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Account

@Repository
interface AccountRepository : JpaRepository<Account, Long> {
  fun findByPrisonNumberAndSubAccountType(prisonNumber: String, subAccountType: String): Account?
  fun findByPrisonNumberAndAccountCode(prisonNumber: String, accountCode: Int): Account?
  fun findByPrisonId(prisonId: Long): List<Account>
  fun findByPrisonNumber(prisonNumber: String): List<Account>
  fun findByPrisonIdAndAccountCodeAndPrisonNumberIsNull(prisonId: Long, accountCode: Int): Account?
}
