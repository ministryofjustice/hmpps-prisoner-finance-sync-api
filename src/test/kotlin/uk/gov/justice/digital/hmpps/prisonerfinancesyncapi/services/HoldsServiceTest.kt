package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.services

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.models.holds.SyncCreateHoldRequest
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("Holds Service Test")
class HoldsServiceTest {

  @Nested
  @DisplayName("Create Hold")
  inner class CreateHold {

    @Test
    fun `should send the hold request to the hold service, store the mapping and return the created hold`() {

      val holdRequest = SyncCreateHoldRequest(
        prisonNumber = "AD23451",
        subAccountCode = 2102,
        holdNumber = 123456789,
        createdAt = LocalDateTime.now(),
        createdBy = "USER",
        holdFromDate = LocalDateTime.now(),
        holdUntilDate = LocalDateTime.now().plusDays(1),
        isReleased = false,
        description = "Test Hold",
        holdType = "WHF",
        holdLocation = "LEI",
        amount = BigDecimal(99.99)
      )




    }

  }

}