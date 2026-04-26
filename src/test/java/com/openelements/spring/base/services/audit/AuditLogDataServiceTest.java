package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("AuditLogDataService finders (unit)")
class AuditLogDataServiceTest {

  private final AuditLogRepository repository = mock(AuditLogRepository.class);

  private final AuditLogDataService service =
      new AuditLogDataService(repository, mock(ApplicationEventPublisher.class));

  @Test
  void findByEntityTypeShouldDelegateAndMap() {
    // GIVEN
    final AuditLogEntity entity = new AuditLogEntity();
    entity.setEntityType("BookDto");
    entity.setEntityId(UUID.randomUUID());
    entity.setAction(AuditAction.INSERT);
    entity.setUserName("alice");
    when(repository.findByEntityType("BookDto")).thenReturn(List.of(entity));

    // WHEN
    final List<AuditLogDto> result = service.findByEntityType("BookDto");

    // THEN
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().entityType()).isEqualTo("BookDto");
    assertThat(result.getFirst().user()).isEqualTo("alice");
  }

  @Test
  void findByUserShouldDelegateAndMap() {
    final AuditLogEntity entity = new AuditLogEntity();
    entity.setEntityType("UserDto");
    entity.setEntityId(UUID.randomUUID());
    entity.setAction(AuditAction.UPDATE);
    entity.setUserName("bob");
    when(repository.findByUserName("bob")).thenReturn(List.of(entity));

    final List<AuditLogDto> result = service.findByUser("bob");

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().action()).isEqualTo(AuditAction.UPDATE);
  }

  @Test
  void findByEntityTypeAndUserShouldDelegateAndMap() {
    final AuditLogEntity entity = new AuditLogEntity();
    entity.setEntityType("BookDto");
    entity.setEntityId(UUID.randomUUID());
    entity.setAction(AuditAction.DELETE);
    entity.setUserName("alice");
    when(repository.findByEntityTypeAndUserName("BookDto", "alice")).thenReturn(List.of(entity));

    final List<AuditLogDto> result = service.findByEntityTypeAndUser("BookDto", "alice");

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().action()).isEqualTo(AuditAction.DELETE);
  }

  @Test
  void findByEntityTypeShouldReturnEmptyWhenNoMatches() {
    when(repository.findByEntityType("NoteDto")).thenReturn(List.of());

    assertThat(service.findByEntityType("NoteDto")).isEmpty();
  }

  @Test
  void findersShouldRejectNull() {
    assertThatThrownBy(() -> service.findByEntityType(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.findByUser(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.findByEntityTypeAndUser(null, "alice"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.findByEntityTypeAndUser("BookDto", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createEntryShouldRejectNull() {
    assertThatThrownBy(
            () -> service.createEntry(null, UUID.randomUUID(), AuditAction.INSERT, "alice"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.createEntry("BookDto", null, AuditAction.INSERT, "alice"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.createEntry("BookDto", UUID.randomUUID(), null, "alice"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> service.createEntry("BookDto", UUID.randomUUID(), AuditAction.INSERT, null))
        .isInstanceOf(NullPointerException.class);
  }
}
