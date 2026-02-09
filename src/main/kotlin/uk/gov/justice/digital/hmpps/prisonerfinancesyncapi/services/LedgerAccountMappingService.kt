package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services
import org.springframework.stereotype.Service

@Service
class LedgerAccountMappingService {

  data class PrisonAccountDetails(val code: Int, val txType: String)

  val prisonerSubAccounts = mapOf("CASH" to 2101, "SPENDS" to 2102, "SAVINGS" to 2103)
  val reversedPrisonerSubAccounts = prisonerSubAccounts.entries.associateBy({ it.value }, { it.key })

  fun mapPrisonerSubAccount(nomisAccountCode: Int): String = reversedPrisonerSubAccounts[nomisAccountCode]
    ?: throw IllegalArgumentException("Unknown NOMIS Prisoner Account Code: $nomisAccountCode")

  fun mapPrisonSubAccount(nomisAccountCode: Int, transactionType: String): String = "$nomisAccountCode:$transactionType"

  fun mapSubAccountGLReferenceToNOMIS(referenceGLCode: String): PrisonAccountDetails {
    val splitReference = referenceGLCode.trim().split(":")

    if (splitReference.size != 2) {
      throw IllegalArgumentException("GL Reference code should only have 2 elements")
    }

    val code = splitReference[0].toIntOrNull()
      ?: throw IllegalArgumentException("GL Reference code $referenceGLCode is not a valid number")

    return PrisonAccountDetails(code, splitReference[1].trim())
  }

  fun mapSubAccountPrisonerReferenceToNOMIS(referenceGLCode: String): Int = prisonerSubAccounts[referenceGLCode] ?: throw IllegalArgumentException("Unknown GL Prisoner reference Code: $referenceGLCode")

  fun isValidPrisonerAccountCode(prisonerAccountCode: Int): Boolean {
    try {
      mapPrisonerSubAccount(prisonerAccountCode)
      return true
    } catch (_: IllegalArgumentException) {
      return false
    }
  }
}
