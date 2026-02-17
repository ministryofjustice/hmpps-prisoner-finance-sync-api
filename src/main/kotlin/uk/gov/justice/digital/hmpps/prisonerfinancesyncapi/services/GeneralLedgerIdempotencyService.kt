package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

@Service
class GeneralLedgerIdempotencyService {
  fun genTransactionIdempotencyKey(transactionId: Long, entrySequence: Int): UUID {
    val seed = "NOMIS-$transactionId-$entrySequence"

    val digest = MessageDigest.getInstance("SHA-1")
    val bytes = digest.digest(seed.toByteArray(StandardCharsets.UTF_8))

    // Set Version to 5 (SHA-1) & Variant to 2 (IETF)
    bytes[6] = (bytes[6].toInt() and 0x0f or 0x50).toByte()
    bytes[8] = (bytes[8].toInt() and 0x3f or 0x80).toByte()

    val buffer = ByteBuffer.wrap(bytes)
    return UUID(buffer.getLong(0), buffer.getLong(8))
  }
}
