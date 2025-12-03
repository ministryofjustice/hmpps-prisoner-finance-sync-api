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
  @param:Autowired val entityManager: TestEntityManager,
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
    assertThat(loaded.txnType).isEqualTo(entity.txnType)
    assertThat(loaded.description).isEqualTo(entity.description)
    assertThat(loaded.activeFlag).isEqualTo(entity.activeFlag)
    assertThat(loaded.txnUsage).isEqualTo(entity.txnUsage)
    assertThat(loaded.allCaseloadFlag).isEqualTo(entity.allCaseloadFlag)
    assertThat(loaded.expiryDate).isEqualTo(entity.expiryDate)
    assertThat(loaded.updateAllowedFlag).isEqualTo(entity.updateAllowedFlag)
    assertThat(loaded.manualInvoiceFlag).isEqualTo(entity.manualInvoiceFlag)
    assertThat(loaded.creditObligationType).isEqualTo(entity.creditObligationType)
    assertThat(loaded.listSeq).isEqualTo(entity.listSeq)
    assertThat(loaded.grossNetFlag).isEqualTo(entity.grossNetFlag)
    assertThat(loaded.caseloadType).isEqualTo(entity.caseloadType)
  }
}
