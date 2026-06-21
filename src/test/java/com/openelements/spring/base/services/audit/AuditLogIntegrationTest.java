package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
import com.openelements.spring.base.services.tag.TagDataService;
import com.openelements.spring.base.services.tag.TagDto;
import com.openelements.spring.base.services.tag.TagRepository;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
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

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockBean private AuthService authService;

  private UserEntity alice;
  private UserEntity bob;

  private static java.util.Optional<UserInformation> userInfo(final UserEntity user) {
    return java.util.Optional.of(
        new UserInformation(
            user.getSub(),
            user.getName(),
            "test@example.com",
            null,
            user.getSub(),
            user.getSub()));
  }

  @BeforeEach
  void setUp() {
    tagRepository.deleteAll();
    auditLogRepository.deleteAll();
    userRepository.deleteAll();
    persistSystemUser();
    alice = saveUser("alice-sub", "alice");
    bob = saveUser("bob-sub", "bob");
  }

  private void persistSystemUser() {
    jdbcTemplate.update(
        "INSERT INTO users (id, sub, user_name, name, active, updated_at, created_at) "
            + "VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        SystemUser.ID,
        SystemUser.SUB,
        SystemUser.SUB,
        SystemUser.NAME);
  }

  private UserEntity saveUser(final String sub, final String name) {
    final UserEntity user = new UserEntity();
    user.setSub(sub);
    user.setExternalId(sub);
    user.setUserName(sub);
    user.setName(name);
    return userRepository.save(user);
  }

  private AuditLogDto seed(
      final UserEntity user, final String entityType, final AuditAction action) {
    when(authService.getUserInformation()).thenReturn(userInfo(user));
    return auditLogDataService.createEntry(entityType, UUID.randomUUID(), "name", action, user);
  }

  @Nested
  @DisplayName("event-driven audit entry creation")
  class EventDriven {

    @Test
    void shouldRecordCreateEventForAuthenticatedUser() {
      when(authService.getUserInformation()).thenReturn(userInfo(alice));

      final TagDto saved = tagDataService.save(new TagDto(null, "tag-create", "desc", "#aabbcc"));

      final Page<AuditLogDto> entries =
          auditLogDataService.findByEntityType("TagDto", Pageable.unpaged());
      assertThat(entries).hasSize(1);
      assertThat(entries.getContent().getFirst().entityId()).isEqualTo(saved.id());
      assertThat(entries.getContent().getFirst().action()).isEqualTo(AuditAction.INSERT);
      assertThat(entries.getContent().getFirst().user().id()).isEqualTo(alice.id());
      assertThat(entries.getContent().getFirst().user().name()).isEqualTo("alice");
    }

    @Test
    void shouldRecordUpdateEvent() {
      when(authService.getUserInformation()).thenReturn(userInfo(bob));
      final TagDto saved = tagDataService.save(new TagDto(null, "tag-update", "desc", "#aabbcc"));

      tagDataService.save(new TagDto(saved.id(), "tag-update", "new-desc", "#aabbcc"));

      final Page<AuditLogDto> entries =
          auditLogDataService.findByUser(bob.id(), Pageable.unpaged());
      assertThat(entries.getContent()).extracting(AuditLogDto::action).contains(AuditAction.UPDATE);
    }

    @Test
    void shouldRecordDeleteEvent() {
      when(authService.getUserInformation()).thenReturn(userInfo(alice));
      final TagDto saved = tagDataService.save(new TagDto(null, "tag-delete", "desc", "#aabbcc"));

      tagDataService.delete(saved);

      final Page<AuditLogDto> entries =
          auditLogDataService.findByEntityTypeAndUser("TagDto", alice.id(), Pageable.unpaged());
      assertThat(entries.getContent()).extracting(AuditLogDto::action).contains(AuditAction.DELETE);
    }

    @Test
    void shouldUseSystemUserWhenNoAuthentication() {
      when(authService.getUserInformation()).thenThrow(new IllegalStateException("no auth"));

      tagDataService.save(new TagDto(null, "tag-system", "desc", "#aabbcc"));

      final Page<AuditLogDto> entries =
          auditLogDataService.findByEntityTypeAndUser("TagDto", SystemUser.ID, Pageable.unpaged());
      assertThat(entries).hasSize(1);
      assertThat(entries.getContent().getFirst().user().name()).isEqualTo(SystemUser.NAME);
    }

    @Test
    void shouldUseSystemUserForApiKeyPrincipal() {
      when(authService.getUserInformation()).thenReturn(java.util.Optional.empty());

      tagDataService.save(new TagDto(null, "tag-api-key", "desc", "#aabbcc"));

      final Page<AuditLogDto> entries =
          auditLogDataService.findByEntityTypeAndUser("TagDto", SystemUser.ID, Pageable.unpaged());
      assertThat(entries).hasSize(1);
    }
  }

  @Nested
  @DisplayName("recursion prevention")
  class RecursionPrevention {

    @Test
    void shouldNotCreateAuditEntryForAuditEntry() {
      when(authService.getUserInformation()).thenReturn(userInfo(alice));

      auditLogDataService.createEntry(
          "BookDto", UUID.randomUUID(), "name", AuditAction.INSERT, alice);

      final List<AuditLogDto> all = auditLogDataService.getAll();
      assertThat(all).hasSize(1);
      assertThat(auditLogDataService.findByEntityType("AuditLogDto", Pageable.unpaged())).isEmpty();
    }
  }

  @Nested
  @DisplayName("query methods")
  class QueryMethods {

    @Test
    void shouldFindByEntityType() {
      seed(alice, "BookDto", AuditAction.INSERT);
      seed(bob, "BookDto", AuditAction.UPDATE);
      seed(alice, "UserDto", AuditAction.INSERT);

      assertThat(auditLogDataService.findByEntityType("BookDto", Pageable.unpaged())).hasSize(2);
    }

    @Test
    void shouldFindByUser() {
      seed(alice, "BookDto", AuditAction.INSERT);
      seed(alice, "UserDto", AuditAction.INSERT);
      seed(bob, "BookDto", AuditAction.UPDATE);

      assertThat(auditLogDataService.findByUser(alice.id(), Pageable.unpaged())).hasSize(2);
    }

    @Test
    void shouldFindByEntityTypeAndUser() {
      seed(alice, "BookDto", AuditAction.INSERT);
      seed(bob, "BookDto", AuditAction.UPDATE);
      seed(alice, "UserDto", AuditAction.INSERT);

      assertThat(
              auditLogDataService.findByEntityTypeAndUser(
                  "BookDto", alice.id(), Pageable.unpaged()))
          .hasSize(1);
    }

    @Test
    void shouldReturnEmptyListWhenNoMatch() {
      assertThat(auditLogDataService.findByEntityType("NoteDto", Pageable.unpaged())).isEmpty();
    }

    @Test
    void shouldFindSystemAttributedEntries() {
      when(authService.getUserInformation()).thenThrow(new IllegalStateException("no auth"));
      final TagDto saved =
          tagDataService.save(new TagDto(null, "tag-system-find", "desc", "#aabbcc"));

      // findByUser may also include WebhookDto UPDATE entries written by WebhookEventListener
      // (which fires AFTER_COMMIT on OnObjectCreate and updates its tracking row). Filter to the
      // entry we care about by entity id rather than asserting an exact count.
      final Page<AuditLogDto> entries =
          auditLogDataService.findByUser(SystemUser.ID, Pageable.unpaged());
      assertThat(entries.getContent())
          .anyMatch(e -> saved.id().equals(e.entityId()) && "TagDto".equals(e.entityType()));
    }
  }

  @Nested
  @DisplayName("retention cleanup")
  class RetentionCleanup {

    @Test
    void shouldDeleteEntriesOlderThanRetention() {
      final AuditLogDto stale = seed(alice, "BookDto", AuditAction.INSERT);
      final AuditLogDto fresh = seed(alice, "BookDto", AuditAction.INSERT);
      backdate(stale.id(), Instant.now().minus(91, ChronoUnit.DAYS));
      backdate(fresh.id(), Instant.now().minus(89, ChronoUnit.DAYS));

      final AuditCleanupJob job = new AuditCleanupJob(auditLogRepository, new AuditProperties(90));

      job.cleanupExpiredEntries();

      assertThat(auditLogRepository.findById(stale.id())).isEmpty();
      assertThat(auditLogRepository.findById(fresh.id())).isPresent();
    }

    @Test
    void shouldDoNothingWhenAllEntriesAreFresh() {
      seed(alice, "BookDto", AuditAction.INSERT);
      seed(bob, "BookDto", AuditAction.INSERT);

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
}
