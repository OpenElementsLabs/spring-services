package com.openelements.spring.base.services.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
import com.openelements.spring.base.services.audit.AuditAction;
import com.openelements.spring.base.services.audit.AuditLogEntity;
import com.openelements.spring.base.services.audit.AuditLogRepository;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import com.openelements.spring.base.services.user.UserService;
import com.openelements.spring.base.testcontainers.PostgresTestConfiguration;
import com.openelements.spring.base.testcontainers.TestApplication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link CommentService} against a real Postgres database.
 *
 * <h2>What is tested</h2>
 *
 * <p>Four orthogonal concerns of the comment subsystem:
 *
 * <ol>
 *   <li><b>Entity mapping.</b> The DDL is what we expect — the {@code comments} table has an
 *       {@code author_id uuid NOT NULL} column with the {@code fk_comments_author} foreign key,
 *       and the {@code @ManyToOne(fetch=LAZY)} association is a real Hibernate proxy that only
 *       initialises on dereference.
 *   <li><b>Comment creation.</b> A saved comment is associated with the currently authenticated
 *       user; on first login the user is provisioned JIT and the comment correctly references
 *       the new row; missing authentication fails the entire transaction so neither comment
 *       nor user is persisted.
 *   <li><b>Comment retrieval.</b> {@code findById} returns a {@link CommentDto} whose nested
 *       {@code AuthorDto} comes from the JPA fetch, not a side-channel call to {@link
 *       UserService}. {@code getAll} on 100 distinct authors stays under 10 SQL statements
 *       thanks to {@code @BatchSize(50)} on {@link UserEntity} — a regression that drops the
 *       annotation would issue 101 queries and fail the assertion.
 *   <li><b>Referential integrity.</b> A comment with an unknown {@code author_id} is rejected by
 *       Postgres; a user with comments cannot be deleted (FK ON DELETE RESTRICT); a user with no
 *       comments can be deleted normally.
 *   <li><b>Audit integration.</b> Create, update, and delete each emit exactly one matching
 *       {@code AuditLogEntity} row with the expected {@code AuditAction}.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>{@link SpringBootTest} loads {@link TestApplication}; {@link PostgresTestConfiguration}
 * spins up a real Postgres via Testcontainers. The {@link JdbcTemplate} is used to introspect
 * {@code information_schema} (the only way to verify the actual DDL — Hibernate metadata would
 * just echo the entity mapping). Hibernate {@link Statistics} is used for the batch-fetch test
 * because the invariant under test (number of SQL statements) is itself a Hibernate-specific
 * behaviour; production code stays on standard JPA APIs.
 *
 * <p><b>Mock-Audit.</b>
 *
 * <ul>
 *   <li>{@code @MockitoBean AuthService} — the test drives the service from outside the Spring
 *       Security filter chain, so the {@code SecurityContextHolder} is not populated. Mocking
 *       lets each test choose the JWT identity per-call via {@code setAuthenticatedUser(...)}.
 *   <li>{@code @MockitoSpyBean UserService} — wraps the real bean so the
 *       "no separate lookup" assertion in {@code shouldReturnAuthorDtoWithoutSeparateLookup} can
 *       call {@code verify(userService, never()).findById(...)}. All real provisioning and
 *       drift-sync behaviour still runs.
 * </ul>
 */
@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestConfiguration.class)
@Testcontainers
@ActiveProfiles("testcontainers")
@DisplayName("CommentService integration")
class CommentServiceIntegrationTest {

  @Autowired private CommentService commentService;
  @Autowired private CommentRepository commentRepository;
  @MockitoSpyBean private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @PersistenceContext private EntityManager entityManager;

  @MockitoBean private AuthService authService;

  private static CommentDto blankComment(final String text) {
    return new CommentDto(null, text, null, null, null);
  }

  @BeforeEach
  void setUp() {
    // Order matters: comments hold an FK to users, so they must go first. Audit rows are
    // generated by the comment-delete events fired above, so they're cleared after.
    commentRepository.deleteAll();
    auditLogRepository.deleteAll();
    userRepository.deleteAll();
  }

  private void setAuthenticatedUser(final String sub, final String name, final String email) {
    when(authService.getUserInformation())
        .thenReturn(
            java.util.Optional.of(new UserInformation(sub, name, email, null, sub, sub)));
  }

  private UserEntity seedUser(final String sub, final String name) {
    final UserEntity user = new UserEntity();
    user.setSub(sub);
    user.setExternalId(sub);
    user.setUserName(sub);
    user.setName(name);
    user.setEmail(sub + "@example.com");
    return userRepository.saveAndFlush(user);
  }

  private CommentEntity seedComment(final UserEntity author, final String text) {
    final CommentEntity comment = new CommentEntity();
    comment.setText(text);
    comment.setAuthor(author);
    return commentRepository.saveAndFlush(comment);
  }

  @Nested
  @DisplayName("entity mapping")
  class EntityMapping {

    /**
     * Asserts the DDL directly against {@code information_schema} rather than via Hibernate
     * metadata — Hibernate would just echo back what the entity declares, while a query against
     * the running Postgres catches schema drift, missing migrations, and naming mistakes.
     */
    @Test
    @DisplayName(
        "The comments table exposes a non-null author_id UUID column and a fk_comments_author FK — no stray 'author' column.")
    void shouldHaveAuthorIdColumnAndForeignKeyConstraint() {
      final List<String> columns =
          jdbcTemplate.queryForList(
              "SELECT column_name FROM information_schema.columns WHERE table_name = 'comments'",
              String.class);
      assertThat(columns).contains("author_id").doesNotContain("author");

      final String dataType =
          jdbcTemplate.queryForObject(
              "SELECT data_type FROM information_schema.columns "
                  + "WHERE table_name = 'comments' AND column_name = 'author_id'",
              String.class);
      assertThat(dataType).isEqualTo("uuid");

      final String isNullable =
          jdbcTemplate.queryForObject(
              "SELECT is_nullable FROM information_schema.columns "
                  + "WHERE table_name = 'comments' AND column_name = 'author_id'",
              String.class);
      assertThat(isNullable).isEqualTo("NO");

      final List<String> fks =
          jdbcTemplate.queryForList(
              "SELECT constraint_name FROM information_schema.table_constraints "
                  + "WHERE table_name = 'comments' AND constraint_type = 'FOREIGN KEY'",
              String.class);
      assertThat(fks).contains("fk_comments_author");
    }

    @Test
    @Transactional
    @DisplayName(
        "findById returns a CommentEntity whose author proxy is uninitialised — fetch is LAZY until dereferenced.")
    void shouldNotInitializeAuthorProxyOnFindById() {
      final UserEntity author = seedUser("sub-lazy", "Lazy");
      final CommentEntity persisted = seedComment(author, "lazy text");
      entityManager.clear();

      final CommentEntity loaded = commentRepository.findById(persisted.getId()).orElseThrow();

      assertThat(Hibernate.isInitialized(loaded.getAuthor()))
          .as("author proxy should not be initialized before getAuthor() is dereferenced")
          .isFalse();
    }

    @Test
    @Transactional
    @DisplayName(
        "Reading getAuthor().getName() inside the transaction triggers the lazy load — the proxy initialises on demand.")
    void shouldLoadAuthorOnDereferenceInsideTransaction() {
      final UserEntity author = seedUser("sub-fetch", "Fetcher");
      final CommentEntity persisted = seedComment(author, "fetch text");
      entityManager.clear();

      final CommentEntity loaded = commentRepository.findById(persisted.getId()).orElseThrow();
      final String name = loaded.getAuthor().getName();

      assertThat(name).isEqualTo("Fetcher");
      assertThat(Hibernate.isInitialized(loaded.getAuthor())).isTrue();
    }
  }

  @Nested
  @DisplayName("comment creation")
  class CommentCreation {

    @Test
    @DisplayName(
        "save() stamps the comment with the authenticated user's id — the author_id column matches the returned author DTO.")
    void shouldAssociateNewCommentWithCurrentUser() {
      seedUser("sub-existing", "Existing");
      setAuthenticatedUser("sub-existing", "Existing", "existing");

      final CommentDto saved = commentService.save(blankComment("first comment"));

      assertThat(saved.id()).isNotNull();
      assertThat(saved.text()).isEqualTo("first comment");
      assertThat(saved.author().name()).isEqualTo("Existing");

      final UUID storedAuthorId =
          jdbcTemplate.queryForObject(
              "SELECT author_id FROM comments WHERE id = ?", UUID.class, saved.id());
      assertThat(storedAuthorId).isEqualTo(saved.author().id());
    }

    @Test
    @DisplayName(
        "A first-time commenter is JIT-provisioned mid-save() — the new comment references the brand-new user's id.")
    void shouldProvisionUserOnFirstLoginAndLinkComment() {
      setAuthenticatedUser("sub-brand-new", "Newcomer", "new");

      final CommentDto saved = commentService.save(blankComment("hi from a new user"));

      final UserEntity provisioned = userRepository.findBySub("sub-brand-new").orElseThrow();
      assertThat(saved.author().id()).isEqualTo(provisioned.getId());

      final Long fkCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM comments WHERE author_id = ?", Long.class, provisioned.getId());
      assertThat(fkCount).isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "save() with no JWT context fails fast — no comment row is written and no user is provisioned.")
    void shouldFailWhenNoAuthentication() {
      when(authService.getUserInformation()).thenThrow(new IllegalStateException("no auth"));

      assertThatThrownBy(() -> commentService.save(blankComment("nope")))
          .isInstanceOf(IllegalStateException.class);

      assertThat(commentRepository.count()).isZero();
      assertThat(userRepository.count()).isZero();
    }
  }

  @Nested
  @DisplayName("comment retrieval")
  class CommentRetrieval {

    /**
     * Pins the "no side-channel" rule: the {@code AuthorDto} on a returned {@link CommentDto}
     * must come from the JPA fetch graph, never from a separate {@code userService.findById}
     * call. Verified with a Mockito spy assertion {@code verify(userService, never())} — a
     * future refactor that re-introduces a second lookup would fail this test.
     */
    @Test
    @DisplayName(
        "findById returns the author DTO straight from the JPA fetch — UserService.findById is never invoked.")
    void shouldReturnAuthorDtoWithoutSeparateLookup() {
      final UserEntity author = seedUser("sub-reader", "Reader");
      final CommentEntity persisted = seedComment(author, "readable");

      final CommentDto found = commentService.findById(persisted.getId()).orElseThrow();

      assertThat(found.author().id()).isEqualTo(author.getId());
      assertThat(found.author().name()).isEqualTo("Reader");
      verify(userService, never()).findById(any(UUID.class));
    }

    /**
     * Regression guard against re-introducing N+1 on the author fetch. Seeds 100 distinct
     * users with one comment each (1:1 cardinality is deliberate — 5 users / 100 comments
     * would silently pass via session-cache deduplication even without batching). With
     * {@code @BatchSize(50)} on {@link UserEntity}, 100 distinct author proxies initialise in
     * {@code ceil(100/50) = 2} batched SELECTs; total ≈ 3 statements with headroom. Without
     * batching this would be 1 + 100 = 101 statements. The {@code ≤ 10} ceiling leaves room
     * for Hibernate-internal bookkeeping while still catching a dropped annotation.
     */
    @Test
    @Transactional
    @DisplayName(
        "Listing 100 comments with 100 distinct authors stays under 10 SQL statements — @BatchSize(50) keeps N+1 dead.")
    void shouldBatchFetchAuthorsForLargeLists() {
      // 100 distinct users with one comment each makes session-cache deduplication
      // irrelevant: without @BatchSize(50) on UserEntity this would issue exactly one
      // user SELECT per comment. We deliberately use 1:1 cardinality (rather than e.g.
      // 5 users / 100 comments) so a regression that drops @BatchSize would actually
      // fail the assertion.
      final List<UserEntity> users = new ArrayList<>(100);
      for (int i = 0; i < 100; i++) {
        users.add(seedUser("sub-batch-" + i, "User" + i));
      }
      for (int i = 0; i < 100; i++) {
        seedComment(users.get(i), "comment-" + i);
      }
      entityManager.clear();

      // Hibernate Statistics is a Hibernate-specific API — used here only because the
      // assertion is itself about a Hibernate batching behaviour. Production code stays
      // on the JPA APIs.
      final Statistics stats =
          entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
      stats.clear();

      final List<CommentDto> all = commentService.getAll();
      all.forEach(
          c -> {
            assertThat(c.author()).isNotNull();
            assertThat(c.author().name()).isNotBlank();
          });

      assertThat(all).hasSize(100);
      // With @BatchSize(50) on UserEntity, 100 distinct author proxies are initialised in
      // ceil(100/50) = 2 batched SELECTs — total ≈3 statements (1 comments + 2 users +
      // headroom). Without batching this would be 1 + 100 = 101 statements. Bound at 10
      // leaves headroom for Hibernate-internal bookkeeping while still proving the N+1
      // is gone.
      final long statements = stats.getPrepareStatementCount();
      assertThat(statements)
          .as("expected ≤10 SQL statements; without @BatchSize this would be 101")
          .isLessThanOrEqualTo(10L);
    }
  }

  @Nested
  @DisplayName("referential integrity")
  class ReferentialIntegrity {

    @Test
    @Transactional
    @DisplayName(
        "Persisting a comment with a phantom author_id throws DataIntegrityViolationException — the FK refuses the insert.")
    void shouldRejectInsertWithUnknownAuthor() {
      final UUID phantomUserId = UUID.randomUUID();
      final UserEntity phantom = entityManager.getReference(UserEntity.class, phantomUserId);

      final CommentEntity comment = new CommentEntity();
      comment.setText("orphan");
      comment.setAuthor(phantom);

      assertThatThrownBy(() -> commentRepository.saveAndFlush(comment))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName(
        "Deleting a user who still owns comments is rejected by the FK — both user and comments remain.")
    void shouldRejectUserDeleteWhenCommentsExist() {
      final UserEntity author = seedUser("sub-locked", "Locked");
      final CommentEntity comment = seedComment(author, "locks the user");

      assertThatThrownBy(
              () -> {
                userRepository.delete(author);
                userRepository.flush();
              })
          .isInstanceOf(DataIntegrityViolationException.class);

      assertThat(commentRepository.findById(comment.getId())).isPresent();
      assertThat(userRepository.findById(author.getId())).isPresent();
    }

    @Test
    @DisplayName("A user with no comments deletes cleanly — the FK only restricts when child rows exist.")
    void shouldAllowUserDeleteWhenNoComments() {
      final UserEntity author = seedUser("sub-orphan", "OrphanUser");

      userRepository.delete(author);
      userRepository.flush();

      assertThat(userRepository.findById(author.getId())).isEmpty();
    }
  }

  @Nested
  @DisplayName("audit log integration")
  class AuditIntegration {

    @Test
    @DisplayName("Creating a comment emits exactly one audit row with action=INSERT for that comment id.")
    void shouldEmitAuditEventOnCreate() {
      seedUser("sub-audit-c", "AuditC");
      setAuthenticatedUser("sub-audit-c", "AuditC", "auditc");

      final CommentDto saved = commentService.save(blankComment("audit me"));

      final List<AuditLogEntity> matches =
          auditLogRepository.findAll().stream()
              .filter(e -> "CommentDto".equals(e.getEntityType()))
              .filter(e -> saved.id().equals(e.getEntityId()))
              .toList();
      assertThat(matches).hasSize(1);
      assertThat(matches.get(0).getAction()).isEqualTo(AuditAction.INSERT);
    }

    @Test
    @DisplayName("Updating an existing comment emits exactly one audit row with action=UPDATE.")
    void shouldEmitAuditEventOnUpdate() {
      seedUser("sub-audit-u", "AuditU");
      setAuthenticatedUser("sub-audit-u", "AuditU", "auditu");
      final CommentDto saved = commentService.save(blankComment("v1"));
      auditLogRepository.deleteAll();

      commentService.save(new CommentDto(saved.id(), "v2", null, null, null));

      final List<AuditLogEntity> matches =
          auditLogRepository.findAll().stream()
              .filter(e -> "CommentDto".equals(e.getEntityType()))
              .filter(e -> saved.id().equals(e.getEntityId()))
              .filter(e -> e.getAction() == AuditAction.UPDATE)
              .toList();
      assertThat(matches).hasSize(1);
    }

    @Test
    @DisplayName("Deleting a comment emits exactly one audit row with action=DELETE for that comment id.")
    void shouldEmitAuditEventOnDelete() {
      seedUser("sub-audit-d", "AuditD");
      setAuthenticatedUser("sub-audit-d", "AuditD", "auditd");
      final CommentDto saved = commentService.save(blankComment("delete me"));
      auditLogRepository.deleteAll();

      commentService.delete(saved);

      final List<AuditLogEntity> matches =
          auditLogRepository.findAll().stream()
              .filter(e -> "CommentDto".equals(e.getEntityType()))
              .filter(e -> saved.id().equals(e.getEntityId()))
              .filter(e -> e.getAction() == AuditAction.DELETE)
              .toList();
      assertThat(matches).hasSize(1);
    }
  }
}
