package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.entities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.integration.RepositoryTestBase
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountCodeLookup
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType

class AccountCodeLookupJpaTest(
  @param:Autowired val entityManager: TestEntityManager,
) : RepositoryTestBase() {

  @Test
  fun `should persist and load AccountCodeLookup`() {
    val parent = AccountCodeLookup(
      accountCode = 10,
      name = "Parent Asset",
      classification = "Asset",
      postingType = PostingType.DR,
      parentAccountCode = null,
    )
    entityManager.persist(parent)

    val entity = AccountCodeLookup(
      accountCode = 100,
      name = "Cash",
      classification = "Asset",
      postingType = PostingType.CR,
      parentAccountCode = 10,
    )
    entityManager.persistAndFlush(entity)

    val found = entityManager.find(AccountCodeLookup::class.java, 100)
    assertThat(found).isEqualTo(entity)
  }

  @Test
  fun `postingType should be mapped as an Enum in JPA metamodel`() {
    val metadata = entityManager
      .entityManager
      .metamodel
      .entity(AccountCodeLookup::class.java)
      .getAttribute("postingType")

    assertThat(metadata.isCollection).isFalse()
    assertThat(metadata.javaType).isEqualTo(PostingType::class.java)
  }

  @Test
  fun `should construct AccountCodeLookup if parentAccountCode is null`() {
    val entity = AccountCodeLookup(
      accountCode = 1,
      name = "Cash",
      classification = "Asset",
      postingType = PostingType.CR,
      parentAccountCode = null,
    )

    entityManager.persistAndFlush(entity)

    val loaded = entityManager.find(AccountCodeLookup::class.java, 1)

    assertThat(loaded?.accountCode).isEqualTo(entity.accountCode)
    assertThat(loaded?.name).isEqualTo(entity.name)
    assertThat(loaded?.classification).isEqualTo(entity.classification)
    assertThat(loaded?.postingType).isEqualTo(entity.postingType)
    assertThat(loaded?.parentAccountCode).isNull()
  }
}
