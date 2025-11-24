package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.MigratedPrisonerBalancePayload

interface MigratedPrisonerBalancePayloadRepository : JpaRepository<MigratedPrisonerBalancePayload, Long>
