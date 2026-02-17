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

    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(seed.toByteArray(StandardCharsets.UTF_8))

    val buffer = ByteBuffer.wrap(hash)

    return UUID(buffer.getLong(0), buffer.getLong(8))
  }
}
