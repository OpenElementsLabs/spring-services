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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@DisplayName("AuditLogDataService finders (unit)")
class AuditLogDataServiceTest {

  private final AuditLogRepository repository = mock(AuditLogRepository.class);

  private final AuditLogDataService service =
      new AuditLogDataService(repository, mock(ApplicationEventPublisher.class));

  private static final Pageable DEFAULT_PAGE = PageRequest.of(0, 20);

  @Test
  void findByEntityTypeShouldDelegateAndMap() {
    // GIVEN
    final AuditLogEntity entity = new AuditLogEntity();
    entity.setEntityType("BookDto");
    entity.setEntityId(UUID.randomUUID());
    entity.setAction(AuditAction.INSERT);
    entity.setUserName("alice");
    when(repository.findByEntityTypeOrderByCreatedAtDesc("BookDto", DEFAULT_PAGE))
        .thenReturn(new PageImpl<>(List.of(entity)));

    // WHEN
    final Page<AuditLogDto> result = service.findByEntityType("BookDto", DEFAULT_PAGE);

    // THEN
    assertThat(result).hasSize(1);
    assertThat(result.getContent().getFirst().entityType()).isEqualTo("BookDto");
    assertThat(result.getContent().getFirst().user()).isEqualTo("alice");
  }

  @Test
  void findByUserShouldDelegateAndMap() {
    final AuditLogEntity entity = new AuditLogEntity();
    entity.setEntityType("UserDto");
    entity.setEntityId(UUID.randomUUID());
    entity.setAction(AuditAction.UPDATE);
    entity.setUserName("bob");
    when(repository.findByUserNameOrderByCreatedAtDesc("bob", DEFAULT_PAGE))
        .thenReturn(new PageImpl<>(List.of(entity)));

    final Page<AuditLogDto> result = service.findByUser("bob", DEFAULT_PAGE);

    assertThat(result).hasSize(1);
    assertThat(result.getContent().getFirst().action()).isEqualTo(AuditAction.UPDATE);
  }

  @Test
  void findByEntityTypeAndUserShouldDelegateAndMap() {
    final AuditLogEntity entity = new AuditLogEntity();
    entity.setEntityType("BookDto");
    entity.setEntityId(UUID.randomUUID());
    entity.setAction(AuditAction.DELETE);
    entity.setUserName("alice");
    when(repository.findByEntityTypeAndUserNameOrderByCreatedAtDesc(
            "BookDto", "alice", DEFAULT_PAGE))
        .thenReturn(new PageImpl<>(List.of(entity)));

    final Page<AuditLogDto> result =
        service.findByEntityTypeAndUser("BookDto", "alice", DEFAULT_PAGE);

    assertThat(result).hasSize(1);
    assertThat(result.getContent().getFirst().action()).isEqualTo(AuditAction.DELETE);
  }

  @Test
  void findByEntityTypeShouldReturnEmptyWhenNoMatches() {
    when(repository.findByEntityTypeOrderByCreatedAtDesc("NoteDto", DEFAULT_PAGE))
        .thenReturn(Page.empty());

    assertThat(service.findByEntityType("NoteDto", DEFAULT_PAGE)).isEmpty();
  }

  @Test
  void findersShouldRejectNull() {
    assertThatThrownBy(() -> service.findByEntityType(null, DEFAULT_PAGE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.findByUser(null, DEFAULT_PAGE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.findByEntityTypeAndUser(null, "alice", DEFAULT_PAGE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.findByEntityTypeAndUser("BookDto", null, DEFAULT_PAGE))
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
