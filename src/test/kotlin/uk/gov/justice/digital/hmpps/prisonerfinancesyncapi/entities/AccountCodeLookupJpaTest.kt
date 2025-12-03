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
  @param:Autowired val entityManager: TestEntityManager,
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
    assertThat(loaded.name).isEqualTo(entity.name)
    assertThat(loaded.classification).isEqualTo(entity.classification)
    assertThat(loaded.postingType).isEqualTo(entity.postingType)
    assertThat(loaded.parentAccountCode).isEqualTo(entity.parentAccountCode)
    assertThat(loaded.accountCode).isEqualTo(entity.accountCode)
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

    assertThat(loaded.accountCode).isEqualTo(entity.accountCode)
    assertThat(loaded.name).isEqualTo(entity.name)
    assertThat(loaded.classification).isEqualTo(entity.classification)
    assertThat(loaded.postingType).isEqualTo(entity.postingType)
    assertThat(loaded.parentAccountCode).isNull()
  }
}
