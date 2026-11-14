package com.openelements.spring.base.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.hibernate.tool.schema.Action;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tenant guard of {@link AbstractMultitenantEntity} as Hibernate runs it.
 *
 * <h2>What is tested</h2>
 *
 * <p>That {@code checkTenant()} is invoked as a JPA lifecycle callback and aborts a persist without
 * a tenant id, while a persist with one succeeds. Calling the method directly would only prove the
 * {@code if}; whether the provider still honours {@code @PrePersist} on a mapped superclass is what
 * a Hibernate upgrade can break (spec 025).
 *
 * <h2>How it is tested</h2>
 *
 * <p>A plain Hibernate {@link EntityManagerFactory} on an in-memory H2 database with the schema
 * generated from the entity. No Spring context: the guard is pure JPA.
 *
 * <p><b>Mock-Audit.</b> No mocks.
 */
@DisplayName("AbstractMultitenantEntity — tenant guard")
class AbstractMultitenantEntityGuardTest {

  private EntityManagerFactory entityManagerFactory;
  private EntityManager entityManager;

  @BeforeEach
  void openEntityManager() {
    entityManagerFactory =
        new HibernatePersistenceConfiguration("tenant-guard")
            .managedClass(GuardedEntity.class)
            .jdbcUrl("jdbc:h2:mem:tenant-guard;DB_CLOSE_DELAY=-1")
            .jdbcCredentials("sa", "")
            .schemaToolingAction(Action.CREATE_DROP)
            .createEntityManagerFactory();
    entityManager = entityManagerFactory.createEntityManager();
    entityManager.getTransaction().begin();
  }

  @AfterEach
  void closeEntityManager() {
    if (entityManager.getTransaction().isActive()) {
      entityManager.getTransaction().rollback();
    }
    entityManager.close();
    entityManagerFactory.close();
  }

  @Test
  @DisplayName("Persisting an entity without a tenant id is rejected")
  void persistWithoutTenantIsRejected() {
    assertThatThrownBy(() -> entityManager.persist(new GuardedEntity()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Tenant ID must be set before persisting");
  }

  @Test
  @DisplayName("Persisting an entity with a tenant id succeeds")
  void persistWithTenantSucceeds() {
    final GuardedEntity entity = new GuardedEntity();
    entity.setTenantId("tenant-a");

    entityManager.persist(entity);
    entityManager.flush();

    assertThat(entity.getId()).isNotNull();
  }

  /** The smallest tenant-scoped entity. */
  @Entity
  @Table(name = "guarded_entity")
  static class GuardedEntity extends AbstractMultitenantEntity {}
}
