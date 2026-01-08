package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.entities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.RepositoryTestBase

import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.TransactionType

class TransactionTypeJpaTest(
  @param:Autowired val entityManager: TestEntityManager,
) : RepositoryTestBase() {

  @Test
  fun `should persist and load TransactionType`() {
    val entity = TransactionType(
      txnType = "TEST_FT",
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

    val loaded = entityManager.find(TransactionType::class.java, "TEST_FT")

    assertThat(loaded).isNotNull
    assertThat(loaded?.txnType).isEqualTo(entity.txnType)
    assertThat(loaded?.description).isEqualTo(entity.description)
    assertThat(loaded?.activeFlag).isEqualTo(entity.activeFlag)
    assertThat(loaded?.txnUsage).isEqualTo(entity.txnUsage)
    assertThat(loaded?.allCaseloadFlag).isEqualTo(entity.allCaseloadFlag)
    assertThat(loaded?.expiryDate).isEqualTo(entity.expiryDate)
    assertThat(loaded?.updateAllowedFlag).isEqualTo(entity.updateAllowedFlag)
    assertThat(loaded?.manualInvoiceFlag).isEqualTo(entity.manualInvoiceFlag)
    assertThat(loaded?.creditObligationType).isEqualTo(entity.creditObligationType)
    assertThat(loaded?.listSeq).isEqualTo(entity.listSeq)
    assertThat(loaded?.grossNetFlag).isEqualTo(entity.grossNetFlag)
    assertThat(loaded?.caseloadType).isEqualTo(entity.caseloadType)
  }
}
