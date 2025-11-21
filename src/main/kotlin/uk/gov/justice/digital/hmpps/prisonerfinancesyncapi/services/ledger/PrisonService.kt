package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services.ledger

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.Prison
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories.PrisonRepository

@Service
open class PrisonService(
  private val prisonRepository: PrisonRepository,
  private val accountService: AccountService,
) {

  fun getPrison(prisonId: String): Prison? = prisonRepository.findByCode(prisonId)

  @Transactional
  open fun createPrison(prisonId: String): Prison {
    val prison = Prison(code = prisonId)
    val savedPrison = prisonRepository.save(prison)

    // Automatically create the core general ledger accounts for the new prison
    accountService.createGeneralLedgerAccount(savedPrison.id!!, 2101)
    accountService.createGeneralLedgerAccount(savedPrison.id, 2102)
    accountService.createGeneralLedgerAccount(savedPrison.id, 2103)

    return savedPrison
  }
}
