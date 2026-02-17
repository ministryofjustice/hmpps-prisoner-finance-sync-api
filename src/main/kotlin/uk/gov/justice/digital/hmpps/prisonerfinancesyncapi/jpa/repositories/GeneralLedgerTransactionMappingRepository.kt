package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.GeneralLedgerTransactionMapping

@Repository
interface GeneralLedgerTransactionMappingRepository : JpaRepository<GeneralLedgerTransactionMapping, Long>
