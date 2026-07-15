package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.data.DbSchema;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration tests for the audit-log subsystem against a real Postgres database.
 *
 * <h2>What is tested</h2>
 *
 * <p>The full event-to-database round trip that unit tests cannot verify:
 *
 * <ol>
 *   <li><b>Event-driven entry creation.</b> Calls to {@link TagDataService#save} / {@code delete}
 *       publish domain events that {@link AuditLogEventListener} translates into persisted
 *       {@link AuditLogEntity} rows — verified by reading them back through {@link
 *       AuditLogDataService}'s finders.
 *   <li><b>SystemUser fallback (in DB).</b> Missing or non-JWT authentication causes entries to
 *       be attributed to {@link SystemUser} in the actual {@code audit_log.user_id} column.
 *   <li><b>Recursion prevention (in DB).</b> Writing an audit entry must not itself produce
 *       another audit entry — verified by inspecting the persisted table.
 *   <li><b>Query methods on real data.</b> {@code findByEntityType}, {@code findByUser},
 *       {@code findByEntityTypeAndUser} against multi-user, multi-type fixtures.
 *   <li><b>Retention cleanup.</b> {@link AuditCleanupJob#cleanupExpiredEntries()} deletes only
 *       entries older than {@code AuditProperties.retentionDays()}; the boundary is checked at
 *       91 days (deleted) vs 89 days (kept) against a 90-day window.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * starts a real Postgres via Testcontainers. {@link TagDataService} is used as a real publisher
 * of {@code OnObjectCreate/Update/Delete} events so the integration is end-to-end.
 *
 * <p><b>Mock-Audit.</b> {@code AuthService} is the single {@code @MockitoBean} — real
 * {@code AuthService} reads from {@link
 * org.springframework.security.core.context.SecurityContextHolder} populated by the filter
 * chain, which the tests deliberately bypass to drive listener decisions directly. Every other
 * collaborator (data service, repositories, cleanup job, tag service) is a real Spring bean
 * backed by real Postgres — there is no mocking of the components under test.
 *
 * <p>The {@code system} user row is inserted via raw JDBC in {@link #persistSystemUser()} because
 * the production data is provisioned by a Flyway migration that does not run in this test
 * profile.
 */
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

  @MockitoBean private AuthService authService;

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
        "INSERT INTO "
            + DbSchema.NAME
            + ".users (id, sub, user_name, name, active, updated_at, created_at) "
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
    @DisplayName(
        "Saving a new Tag while alice is authenticated persists an INSERT audit row attributed "
            + "to alice — findByEntityType('TagDto') reads it back end-to-end.")
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
    @DisplayName(
        "Re-saving an existing Tag persists an UPDATE audit row attributed to the current user.")
    void shouldRecordUpdateEvent() {
      when(authService.getUserInformation()).thenReturn(userInfo(bob));
      final TagDto saved = tagDataService.save(new TagDto(null, "tag-update", "desc", "#aabbcc"));

      tagDataService.save(new TagDto(saved.id(), "tag-update", "new-desc", "#aabbcc"));

      final Page<AuditLogDto> entries =
          auditLogDataService.findByUser(bob.id(), Pageable.unpaged());
      assertThat(entries.getContent()).extracting(AuditLogDto::action).contains(AuditAction.UPDATE);
    }

    @Test
    @DisplayName("Deleting a Tag persists a DELETE audit row attributed to the current user.")
    void shouldRecordDeleteEvent() {
      when(authService.getUserInformation()).thenReturn(userInfo(alice));
      final TagDto saved = tagDataService.save(new TagDto(null, "tag-delete", "desc", "#aabbcc"));

      tagDataService.delete(saved);

      final Page<AuditLogDto> entries =
          auditLogDataService.findByEntityTypeAndUser("TagDto", alice.id(), Pageable.unpaged());
      assertThat(entries.getContent()).extracting(AuditLogDto::action).contains(AuditAction.DELETE);
    }

    @Test
    @DisplayName(
        "When AuthService throws IllegalStateException (no authentication), the persisted audit "
            + "row is attributed to SystemUser in the database — audit is never silently dropped.")
    void shouldUseSystemUserWhenNoAuthentication() {
      when(authService.getUserInformation()).thenThrow(new IllegalStateException("no auth"));

      tagDataService.save(new TagDto(null, "tag-system", "desc", "#aabbcc"));

      final Page<AuditLogDto> entries =
          auditLogDataService.findByEntityTypeAndUser("TagDto", SystemUser.ID, Pageable.unpaged());
      assertThat(entries).hasSize(1);
      assertThat(entries.getContent().getFirst().user().name()).isEqualTo(SystemUser.NAME);
    }

    @Test
    @DisplayName(
        "An API-key (non-JWT) principal causes the persisted audit row to be attributed to "
            + "SystemUser in the database.")
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

    /**
     * Persistence-level confirmation of the unit-test recursion guard: writing an audit entry
     * must not itself fire an audit event for entity type {@code AuditLogDto}. Asserted by
     * reading the database back after one explicit {@code createEntry} call.
     */
    @Test
    @DisplayName(
        "Persisting one audit entry produces exactly one row — findByEntityType('AuditLogDto') "
            + "stays empty, confirming the recursion guard at the database level.")
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
    @DisplayName(
        "findByEntityType returns only the rows whose entityType matches the argument across a "
            + "multi-type fixture.")
    void shouldFindByEntityType() {
      seed(alice, "BookDto", AuditAction.INSERT);
      seed(bob, "BookDto", AuditAction.UPDATE);
      seed(alice, "UserDto", AuditAction.INSERT);

      assertThat(auditLogDataService.findByEntityType("BookDto", Pageable.unpaged())).hasSize(2);
    }

    @Test
    @DisplayName(
        "findByUser returns every entry attributed to a given user regardless of entity type.")
    void shouldFindByUser() {
      seed(alice, "BookDto", AuditAction.INSERT);
      seed(alice, "UserDto", AuditAction.INSERT);
      seed(bob, "BookDto", AuditAction.UPDATE);

      assertThat(auditLogDataService.findByUser(alice.id(), Pageable.unpaged())).hasSize(2);
    }

    @Test
    @DisplayName(
        "findByEntityTypeAndUser returns the intersection — entries matching both the entity "
            + "type AND the user.")
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
    @DisplayName("findByEntityType returns an empty page when no row matches the entity type.")
    void shouldReturnEmptyListWhenNoMatch() {
      assertThat(auditLogDataService.findByEntityType("NoteDto", Pageable.unpaged())).isEmpty();
    }

    /**
     * Resilient assertion: other listeners ({@code WebhookEventListener}) also write audit rows
     * attributed to {@code SystemUser} via {@code AFTER_COMMIT} hooks, so a flat count would be
     * brittle. The assertion filters by entity id to pin the specific row produced by this test.
     */
    @Test
    @DisplayName(
        "findByUser(SystemUser.ID) surfaces a system-attributed audit row — assertion filters "
            + "by entity id to ignore unrelated AFTER_COMMIT writes from other listeners.")
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

    /**
     * Retention boundary check against a 90-day window: an entry backdated to 91 days ago must
     * be deleted; an entry backdated to 89 days ago must survive. Backdating uses raw SQL
     * because {@code created_at} is mapped {@code updatable=false} on {@link
     * com.openelements.spring.base.data.AbstractEntity}.
     */
    @Test
    @DisplayName(
        "AuditCleanupJob deletes entries older than retentionDays and keeps fresher ones — 91d "
            + "is removed, 89d survives against a 90-day window.")
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
    @DisplayName(
        "AuditCleanupJob is a no-op when every entry is younger than retentionDays — nothing is "
            + "deleted by mistake.")
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
              "UPDATE " + DbSchema.NAME + ".audit_log SET created_at = ? WHERE id = ?",
              Timestamp.from(when),
              id);
      if (updated != 1) {
        throw new IllegalStateException("backdate failed for id " + id);
      }
    }
  }
}
