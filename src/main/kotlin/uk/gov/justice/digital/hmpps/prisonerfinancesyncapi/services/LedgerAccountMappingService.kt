package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services
import org.springframework.stereotype.Service

@Service
class LedgerAccountMappingService {

  data class PrisonAccountDetails(val code: Int, val txType: String)

  fun mapPrisonerSubAccount(nomisAccountCode: Int): String = when (nomisAccountCode) {
    2101 -> "CASH"
    2102 -> "SPENDS"
    2103 -> "SAVINGS"
    else -> throw IllegalArgumentException("Unknown NOMIS Prisoner Account Code: $nomisAccountCode")
  }

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

  fun mapSubAccountPrisonerReferenceToNOMIS(referenceGLCode: String): Int = when (referenceGLCode) {
    "CASH" -> 2101
    "SPENDS" -> 2102
    "SAVINGS" -> 2103
    else -> throw IllegalArgumentException("Unknown GL Prisoner reference Code: $referenceGLCode")
  }

  fun isValidPrisonerAccountCode(prisonerAccountCode: Int): Boolean {
    try {
      mapPrisonerSubAccount(prisonerAccountCode)
      return true
    } catch (_: IllegalArgumentException) {
      return false
    }
  }
}
