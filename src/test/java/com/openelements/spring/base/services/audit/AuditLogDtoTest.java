package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.data.AbstractEntity;
import com.openelements.spring.base.services.user.UserEntity;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

  @Test
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
