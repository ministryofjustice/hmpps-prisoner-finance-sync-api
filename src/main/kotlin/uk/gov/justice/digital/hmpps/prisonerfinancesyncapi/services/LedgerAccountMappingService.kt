package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services
import org.springframework.stereotype.Service

@Service
class LedgerAccountMappingService {

  fun mapPrisonerSubAccount(nomisAccountCode: Int): String = ""

  fun mapPrisonSubAccount(nomisAccountCode: Int, transactionType: String): String = "$nomisAccountCode:$transactionType"
}
