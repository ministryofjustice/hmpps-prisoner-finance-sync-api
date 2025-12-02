package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.entities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.TransactionType

@DataJpaTest
@TestPropertySource(
  // override postgres flyaway migrations and tell hibernate to start empty
  properties = [
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
  ],
)
class TransactionTypeJpaTest(
  @Autowired val entityManager: TestEntityManager,
) {

  @Test
  fun `should persist and load TransactionType`() {
    val entity = TransactionType(
      txnType = "FT",
      description = "Some description",
      activeFlag = "Y",
      txnUsage = "R",
      allCaseloadFlag = "Y",
      expiryDate = "2025-1-12",
      updateAllowedFlag = "Y",
      manualInvoiceFlag = "Y",
      creditObligationType = "",
      listSeq = 99,
      grossNetFlag = "N",
      caseloadType = "INST",
    )
    entityManager.persistAndFlush(entity)

    val loaded = entityManager.find(TransactionType::class.java, "FT")

    assertThat(loaded).isNotNull
    assertThat(loaded.txnType).isEqualTo("FT")
    assertThat(loaded.description).isEqualTo("Some description")
    assertThat(loaded.activeFlag).isEqualTo("Y")
    assertThat(loaded.txnUsage).isEqualTo("R")
    assertThat(loaded.allCaseloadFlag).isEqualTo("Y")
    assertThat(loaded.expiryDate).isEqualTo("2025-1-12")
    assertThat(loaded.updateAllowedFlag).isEqualTo("Y")
    assertThat(loaded.manualInvoiceFlag).isEqualTo("Y")
    assertThat(loaded.creditObligationType).isEqualTo("")
    assertThat(loaded.listSeq).isEqualTo(99)
    assertThat(loaded.grossNetFlag).isEqualTo("N")
    assertThat(loaded!!.caseloadType).isEqualTo("INST")
  }
}
