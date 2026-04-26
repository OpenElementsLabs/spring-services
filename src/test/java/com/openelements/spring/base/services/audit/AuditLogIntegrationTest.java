package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.services.webhook.data.WebhookDataService;
import com.openelements.spring.base.services.webhook.data.WebhookDto;
import com.openelements.spring.base.services.webhook.data.WebhookRepository;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("Audit log integration")
class AuditLogIntegrationTest {

  @Autowired private AuditLogDataService auditLogDataService;

  @Autowired private AuditLogRepository auditLogRepository;

  @Autowired private AuditCleanupJob auditCleanupJob;

  @Autowired private WebhookDataService webhookDataService;

  @Autowired private WebhookRepository webhookRepository;

  @MockBean private AuthService authService;

  @BeforeEach
  void setUp() {
    auditLogRepository.deleteAll();
    webhookRepository.deleteAll();
  }

  @Nested
  @DisplayName("event-driven audit entry creation")
  class EventDriven {

    @Test
    void shouldRecordCreateEventForAuthenticatedUser() {
      // GIVEN
      when(authService.getPrincipal()).thenReturn(principalNamed("alice"));

      // WHEN
      final WebhookDto saved =
          webhookDataService.save(new WebhookDto(null, "https://example.com", true, null, null));

      // THEN
      final List<AuditLogDto> entries = auditLogDataService.findByEntityType("WebhookDto");
      assertThat(entries).hasSize(1);
      assertThat(entries.getFirst().entityId()).isEqualTo(saved.id());
      assertThat(entries.getFirst().action()).isEqualTo(AuditAction.INSERT);
      assertThat(entries.getFirst().user()).isEqualTo("alice");
    }

    @Test
    void shouldRecordUpdateEvent() {
      when(authService.getPrincipal()).thenReturn(principalNamed("bob"));
      final WebhookDto saved =
          webhookDataService.save(new WebhookDto(null, "https://example.com", true, null, null));

      webhookDataService.save(
          new WebhookDto(saved.id(), "https://example.com/updated", true, null, null));

      final List<AuditLogDto> entries = auditLogDataService.findByUser("bob");
      assertThat(entries).extracting(AuditLogDto::action).contains(AuditAction.UPDATE);
    }

    @Test
    void shouldRecordDeleteEvent() {
      when(authService.getPrincipal()).thenReturn(principalNamed("alice"));
      final WebhookDto saved =
          webhookDataService.save(new WebhookDto(null, "https://example.com", true, null, null));

      webhookDataService.delete(saved);

      final List<AuditLogDto> entries =
          auditLogDataService.findByEntityTypeAndUser("WebhookDto", "alice");
      assertThat(entries).extracting(AuditLogDto::action).contains(AuditAction.DELETE);
    }

    @Test
    void shouldUseSystemUserWhenNoAuthentication() {
      when(authService.getPrincipal()).thenThrow(new IllegalStateException("no auth"));

      webhookDataService.save(new WebhookDto(null, "https://example.com", true, null, null));

      final List<AuditLogDto> entries = auditLogDataService.findByUser("System");
      assertThat(entries).hasSize(1);
    }
  }

  @Nested
  @DisplayName("recursion prevention")
  class RecursionPrevention {

    @Test
    void shouldNotCreateAuditEntryForAuditEntry() {
      when(authService.getPrincipal()).thenReturn(principalNamed("alice"));

      auditLogDataService.createEntry(
          "BookDto", java.util.UUID.randomUUID(), AuditAction.INSERT, "alice");

      final List<AuditLogDto> all = auditLogDataService.getAll();
      assertThat(all).hasSize(1);
      assertThat(auditLogDataService.findByEntityType("AuditLogDto")).isEmpty();
    }
  }

  @Nested
  @DisplayName("query methods")
  class QueryMethods {

    @Test
    void shouldFindByEntityType() {
      seed("alice", "BookDto", AuditAction.INSERT);
      seed("bob", "BookDto", AuditAction.UPDATE);
      seed("alice", "UserDto", AuditAction.INSERT);

      assertThat(auditLogDataService.findByEntityType("BookDto")).hasSize(2);
    }

    @Test
    void shouldFindByUser() {
      seed("alice", "BookDto", AuditAction.INSERT);
      seed("alice", "UserDto", AuditAction.INSERT);
      seed("bob", "BookDto", AuditAction.UPDATE);

      assertThat(auditLogDataService.findByUser("alice")).hasSize(2);
    }

    @Test
    void shouldFindByEntityTypeAndUser() {
      seed("alice", "BookDto", AuditAction.INSERT);
      seed("bob", "BookDto", AuditAction.UPDATE);
      seed("alice", "UserDto", AuditAction.INSERT);

      assertThat(auditLogDataService.findByEntityTypeAndUser("BookDto", "alice")).hasSize(1);
    }

    @Test
    void shouldReturnEmptyListWhenNoMatch() {
      assertThat(auditLogDataService.findByEntityType("NoteDto")).isEmpty();
    }
  }

  @Nested
  @DisplayName("retention cleanup")
  class RetentionCleanup {

    @Test
    void shouldDeleteEntriesOlderThanRetention() throws Exception {
      // GIVEN — one stale (91 days old) and one fresh (89 days old) entry, retention 90 days
      final AuditLogDto stale = seed("alice", "BookDto", AuditAction.INSERT);
      final AuditLogDto fresh = seed("alice", "BookDto", AuditAction.INSERT);
      backdate(stale.id(), Instant.now().minus(91, ChronoUnit.DAYS));
      backdate(fresh.id(), Instant.now().minus(89, ChronoUnit.DAYS));

      final AuditCleanupJob job = new AuditCleanupJob(auditLogRepository, new AuditProperties(90));

      // WHEN
      job.cleanupExpiredEntries();

      // THEN
      assertThat(auditLogRepository.findById(stale.id())).isEmpty();
      assertThat(auditLogRepository.findById(fresh.id())).isPresent();
    }

    @Test
    void shouldDoNothingWhenAllEntriesAreFresh() {
      seed("alice", "BookDto", AuditAction.INSERT);
      seed("bob", "BookDto", AuditAction.INSERT);

      auditCleanupJob.cleanupExpiredEntries();

      assertThat(auditLogRepository.count()).isEqualTo(2);
    }

    private void backdate(final java.util.UUID id, final Instant when) throws Exception {
      final AuditLogEntity entity = auditLogRepository.findById(id).orElseThrow();
      final Field field =
          com.openelements.spring.base.data.AbstractEntity.class.getDeclaredField("createdAt");
      field.setAccessible(true);
      field.set(entity, when);
      auditLogRepository.saveAndFlush(entity);
    }
  }

  private AuditLogDto seed(final String user, final String entityType, final AuditAction action) {
    when(authService.getPrincipal()).thenReturn(principalNamed(user));
    return auditLogDataService.createEntry(entityType, java.util.UUID.randomUUID(), action, user);
  }

  private static AuthenticatedPrincipal principalNamed(final String name) {
    return () -> name;
  }
}
