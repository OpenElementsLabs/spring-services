package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.services.tag.TagDataService;
import com.openelements.spring.base.services.tag.TagDto;
import com.openelements.spring.base.services.tag.TagRepository;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

  @Autowired private TagDataService tagDataService;

  @Autowired private TagRepository tagRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockBean private AuthService authService;

  @BeforeEach
  void setUp() {
    auditLogRepository.deleteAll();
    tagRepository.deleteAll();
  }

  @Nested
  @DisplayName("event-driven audit entry creation")
  class EventDriven {

    @Test
    void shouldRecordCreateEventForAuthenticatedUser() {
      // GIVEN
      when(authService.getPrincipal()).thenReturn(principalNamed("alice"));

      // WHEN
      final TagDto saved = tagDataService.save(new TagDto(null, "tag-create", "desc", "#aabbcc"));

      // THEN
      final List<AuditLogDto> entries = auditLogDataService.findByEntityType("TagDto");
      assertThat(entries).hasSize(1);
      assertThat(entries.getFirst().entityId()).isEqualTo(saved.id());
      assertThat(entries.getFirst().action()).isEqualTo(AuditAction.INSERT);
      assertThat(entries.getFirst().user()).isEqualTo("alice");
    }

    @Test
    void shouldRecordUpdateEvent() {
      when(authService.getPrincipal()).thenReturn(principalNamed("bob"));
      final TagDto saved = tagDataService.save(new TagDto(null, "tag-update", "desc", "#aabbcc"));

      tagDataService.save(new TagDto(saved.id(), "tag-update", "new-desc", "#aabbcc"));

      final List<AuditLogDto> entries = auditLogDataService.findByUser("bob");
      assertThat(entries).extracting(AuditLogDto::action).contains(AuditAction.UPDATE);
    }

    @Test
    void shouldRecordDeleteEvent() {
      when(authService.getPrincipal()).thenReturn(principalNamed("alice"));
      final TagDto saved = tagDataService.save(new TagDto(null, "tag-delete", "desc", "#aabbcc"));

      tagDataService.delete(saved);

      final List<AuditLogDto> entries =
          auditLogDataService.findByEntityTypeAndUser("TagDto", "alice");
      assertThat(entries).extracting(AuditLogDto::action).contains(AuditAction.DELETE);
    }

    @Test
    void shouldUseSystemUserWhenNoAuthentication() {
      when(authService.getPrincipal()).thenThrow(new IllegalStateException("no auth"));

      tagDataService.save(new TagDto(null, "tag-system", "desc", "#aabbcc"));

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

      auditLogDataService.createEntry("BookDto", UUID.randomUUID(), AuditAction.INSERT, "alice");

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
    void shouldDeleteEntriesOlderThanRetention() {
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

    /**
     * Updates {@code created_at} via raw SQL because the column is mapped {@code updatable=false}
     * on {@code AbstractEntity}, so JPA silently ignores changes to it on update.
     */
    private void backdate(final UUID id, final Instant when) {
      final int updated =
          jdbcTemplate.update(
              "UPDATE audit_log SET created_at = ? WHERE id = ?", Timestamp.from(when), id);
      if (updated != 1) {
        throw new IllegalStateException("backdate failed for id " + id);
      }
    }
  }

  private AuditLogDto seed(final String user, final String entityType, final AuditAction action) {
    when(authService.getPrincipal()).thenReturn(principalNamed(user));
    return auditLogDataService.createEntry(entityType, UUID.randomUUID(), action, user);
  }

  private static AuthenticatedPrincipal principalNamed(final String name) {
    return () -> name;
  }
}
