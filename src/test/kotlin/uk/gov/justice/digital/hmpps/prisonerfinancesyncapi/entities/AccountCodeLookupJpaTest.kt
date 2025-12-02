package uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.entities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.AccountCodeLookup
import uk.gov.justice.digital.hmpps.prisonerfinancesyncapi.jpa.entities.PostingType

@DataJpaTest
@TestPropertySource(
  // override postgres flyaway migrations and tell hibernate to start empty
  properties = [
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
  ],
)
class AccountCodeLookupJpaTest(
  @Autowired val entityManager: TestEntityManager,
) {

  @Test
  fun `should persist and load AccountCodeLookup`() {
    val entity = AccountCodeLookup(
      accountCode = 100,
      name = "Cash",
      classification = "Asset",
      postingType = PostingType.CR,
      parentAccountCode = 10,
    )

    entityManager.persistAndFlush(entity)

    val loaded = entityManager.find(AccountCodeLookup::class.java, 100)

    assertThat(loaded).isNotNull
    assertThat(loaded!!.name).isEqualTo("Cash")
    assertThat(loaded.postingType).isEqualTo(PostingType.CR)
    assertThat(loaded.parentAccountCode).isEqualTo(10)
    assertThat(loaded.accountCode).isEqualTo(100)
  }

  @Test
  fun `postingType should be stored as string`() {
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

    assertThat(entity.accountCode).isEqualTo(1)
    assertThat(entity.name).isEqualTo("Cash")
    assertThat(entity.classification).isEqualTo("Asset")
    assertThat(entity.postingType).isEqualTo(PostingType.CR)
    assertThat(entity.parentAccountCode).isNull()
  }
}
