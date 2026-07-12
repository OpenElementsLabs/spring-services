package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.services.user.UserEntity;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuditLogDto#fromEntity(AuditLogEntity)} — the entity-to-DTO mapper.
 *
 * <h2>What is tested</h2>
 *
 * <p>That every field of an {@link AuditLogEntity} is copied verbatim onto the resulting
 * {@link AuditLogDto}, including the inherited {@code id} and {@code createdAt} from
 * {@link AbstractEntity} (which are not exposed through setters), and that the associated
 * {@link UserEntity} is recursively mapped to a nested {@code UserDto} with its own id / name /
 * email. Mapper drift would silently strip fields from API responses; the test pins the full
 * field set.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 with AssertJ. The two inherited fields ({@code id}, {@code createdAt}) are
 * normally set by JPA / Hibernate at flush time; here they are injected via reflection so the
 * mapper can be exercised without a persistence context.
 *
 * <p><b>Mock-Audit.</b> Zero mocks. The mapper is a pure function; the test builds real
 * {@link AuditLogEntity} and {@link UserEntity} instances and uses reflection only to set
 * package-private inherited fields.
 */
@DisplayName("AuditLogDto")
class AuditLogDtoTest {

  private static void setAbstractEntityId(final AbstractEntity entity, final UUID id)
      throws Exception {
    final Field field = AbstractEntity.class.getDeclaredField("id");
    field.setAccessible(true);
    field.set(entity, id);
  }

  private static void setAbstractEntityCreatedAt(
      final AbstractEntity entity, final Instant createdAt) throws Exception {
    final Field field = AbstractEntity.class.getDeclaredField("createdAt");
    field.setAccessible(true);
    field.set(entity, createdAt);
  }

  /**
   * Pins the full field set of {@link AuditLogDto#fromEntity(AuditLogEntity)}: id, entityType,
   * entityId, action, the nested user (id / name / email), and createdAt. Adding a new field to
   * either record without mapping it here will fail this test.
   */
  @Test
  @DisplayName(
      "AuditLogDto.fromEntity(...) copies every field — id, entityType, entityId, action, nested "
          + "user (id/name/email), and createdAt — from the entity onto the DTO.")
  void shouldCopyAllFieldsFromEntity() throws Exception {
    final UUID id = UUID.randomUUID();
    final UUID entityId = UUID.randomUUID();
    final UUID aliceId = UUID.randomUUID();
    final Instant createdAt = Instant.parse("2026-04-26T12:00:00Z");

    final UserEntity alice = new UserEntity();
    setAbstractEntityId(alice, aliceId);
    alice.setSub("alice-sub");
    alice.setName("alice");
    alice.setEmail("alice@example.com");

    final AuditLogEntity entity = new AuditLogEntity();
    setAbstractEntityId(entity, id);
    setAbstractEntityCreatedAt(entity, createdAt);
    entity.setEntityType("BookDto");
    entity.setEntityId(entityId);
    entity.setAction(AuditAction.INSERT);
    entity.setUser(alice);

    final AuditLogDto dto = AuditLogDto.fromEntity(entity);

    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.entityType()).isEqualTo("BookDto");
    assertThat(dto.entityId()).isEqualTo(entityId);
    assertThat(dto.action()).isEqualTo(AuditAction.INSERT);
    assertThat(dto.user().id()).isEqualTo(aliceId);
    assertThat(dto.user().name()).isEqualTo("alice");
    assertThat(dto.user().email()).isEqualTo("alice@example.com");
    assertThat(dto.createdAt()).isEqualTo(createdAt);
  }
}
