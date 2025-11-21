package uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinancepocapi.jpa.entities.MigratedGeneralLedgerBalancePayload

interface MigratedGeneralLedgerBalancePayloadRepository : JpaRepository<MigratedGeneralLedgerBalancePayload, Long>
