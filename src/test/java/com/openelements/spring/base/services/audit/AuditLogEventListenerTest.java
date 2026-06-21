package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.openelements.spring.base.data.NameSupplier;
import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import com.openelements.spring.base.security.AuthService;
import com.openelements.spring.base.security.UserInformation;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserDto;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Mockito-style unit tests for {@link AuditLogEventListener} — the bridge between domain events
 * ({@code OnObjectCreate} / {@code OnObjectUpdate} / {@code OnObjectDelete}) and persisted audit
 * entries.
 *
 * <h2>What is tested</h2>
 *
 * <p>Four concerns of the listener:
 *
 * <ol>
 *   <li><b>Action mapping.</b> Create / update / delete events produce {@link AuditAction#INSERT}
 *       / {@code UPDATE} / {@code DELETE} entries respectively, attributed to the authenticated
 *       user looked up by {@code sub}.
 *   <li><b>System-user fallback.</b> Whenever the listener cannot resolve a "real" user — no
 *       authentication, non-JWT principal (API key), blank/null user name, or a JWT subject with
 *       no local row — the entry is attributed to {@link SystemUser} instead of being dropped.
 *       Audit must not have gaps.
 *   <li><b>Recursion prevention &amp; failure swallowing.</b> Events for {@link AuditLogDto} are
 *       ignored (otherwise writing an audit entry would itself trigger one), and an exception
 *       from the data service is swallowed so a downed audit DB cannot bring down the request
 *       path.
 *   <li><b>Display-name resolution via {@link NameSupplier}.</b> A correctly annotated zero-arg
 *       String-returning method supplies the human-readable name; missing, multi-arg, or
 *       wrong-return-type annotated methods fall back to the literal {@code "UNKNOWN"}.
 * </ol>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 with Mockito and AssertJ. No Spring context. Domain payloads are tiny
 * local records ({@code TestData}, {@code NamedData}, {@code BadlyNamedData}) implementing
 * {@link WithId}.
 *
 * <p><b>Mock-Audit.</b> Three mocks, each justified:
 *
 * <ul>
 *   <li>{@code AuditLogDataService} — the listener's job IS to delegate to it with the right
 *       arguments; the {@code verify(...)}-with-captor pattern is the entire test contract.
 *       Using a real instance would require a database and would shift coverage away from the
 *       listener decisions onto persistence concerns already covered by {@link
 *       AuditLogIntegrationTest}.
 *   <li>{@code AuthService} — real {@code AuthService} reads from {@link
 *       org.springframework.security.core.context.SecurityContextHolder}; reconstructing that
 *       here would require Spring Security plumbing that adds no listener coverage.
 *   <li>{@code UserRepository} — stubbing {@code findBySub} and {@code getReferenceById} lets
 *       the tests exercise both "user resolved" and "user missing" branches without persistence.
 * </ul>
 */
@DisplayName("AuditLogEventListener")
class AuditLogEventListenerTest {

  private final AuditLogDataService auditLogDataService = mock(AuditLogDataService.class);
  private final AuthService authService = mock(AuthService.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final AuditLogEventListener listener =
      new AuditLogEventListener(auditLogDataService, authService, userRepository);
  private final UserEntity systemUserRef = new UserEntity();
  private final UserEntity alice = new UserEntity();
  private final UserEntity bob = new UserEntity();

  private static Optional<UserInformation> userNamed(final String sub, final String name) {
    return Optional.of(new UserInformation(sub, name, "test@example.com", null, sub, sub));
  }

  @BeforeEach
  void initUsers() {
    systemUserRef.setId(SystemUser.ID);
    systemUserRef.setSub(SystemUser.SUB);
    systemUserRef.setName(SystemUser.NAME);
    alice.setId(UUID.randomUUID());
    alice.setSub("alice-sub");
    alice.setName("alice");
    bob.setId(UUID.randomUUID());
    bob.setSub("bob-sub");
    bob.setName("bob");
    when(userRepository.getReferenceById(SystemUser.ID)).thenReturn(systemUserRef);
  }

  @Test
  @DisplayName(
      "OnObjectCreate produces an INSERT audit entry attributed to the authenticated user "
          + "resolved via findBySub.")
  void shouldRecordCreateEntryWithAuthenticatedUser() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getUserInformation()).thenReturn(userNamed("alice-sub", "alice"));
    when(userRepository.findBySub("alice-sub")).thenReturn(Optional.of(alice));

    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    verify(auditLogDataService)
        .createEntry(
            eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), eq(alice));
  }

  @Test
  @DisplayName("OnObjectUpdate produces an UPDATE audit entry attributed to the authenticated user.")
  void shouldRecordUpdateEntry() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getUserInformation()).thenReturn(userNamed("bob-sub", "bob"));
    when(userRepository.findBySub("bob-sub")).thenReturn(Optional.of(bob));

    listener.handleOnObjectUpdate(new OnObjectUpdate<>(data));

    verify(auditLogDataService)
        .createEntry(eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.UPDATE), eq(bob));
  }

  @Test
  @DisplayName("OnObjectDelete produces a DELETE audit entry attributed to the authenticated user.")
  void shouldRecordDeleteEntry() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getUserInformation()).thenReturn(userNamed("alice-sub", "alice"));
    when(userRepository.findBySub("alice-sub")).thenReturn(Optional.of(alice));

    listener.handleOnObjectDelete(new OnObjectDelete<>(data));

    verify(auditLogDataService)
        .createEntry(
            eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.DELETE), eq(alice));
  }

  /**
   * When {@code AuthService} itself blows up (the legacy "no authentication" signalling), the
   * listener must still emit an audit entry. The fallback is the {@link SystemUser} — audit must
   * not have gaps.
   */
  @Test
  @DisplayName(
      "An IllegalStateException from AuthService is caught and the entry is attributed to "
          + "SystemUser — audit must never silently drop a domain event.")
  void shouldUseSystemUserWhenNoAuthentication() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getUserInformation())
        .thenThrow(new IllegalStateException("No authentication"));

    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    verify(auditLogDataService)
        .createEntry(
            eq("TestData"),
            eq(data.id()),
            eq("UNKNOWN"),
            eq(AuditAction.INSERT),
            eq(systemUserRef));
  }

  @Test
  @DisplayName(
      "A non-JWT principal (AuthService returns Optional.empty) is attributed to SystemUser.")
  void shouldUseSystemUserWhenPrincipalIsNotAJwt() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getUserInformation()).thenReturn(Optional.empty());

    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    verify(auditLogDataService)
        .createEntry(
            eq("TestData"),
            eq(data.id()),
            eq("UNKNOWN"),
            eq(AuditAction.INSERT),
            eq(systemUserRef));
  }

  @Test
  @DisplayName(
      "A JWT whose user name is whitespace-only is treated as anonymous and attributed to SystemUser.")
  void shouldUseSystemUserWhenUserNameIsBlank() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getUserInformation()).thenReturn(userNamed("sub-id", "   "));

    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    verify(auditLogDataService)
        .createEntry(
            eq("TestData"),
            eq(data.id()),
            eq("UNKNOWN"),
            eq(AuditAction.INSERT),
            eq(systemUserRef));
  }

  @Test
  @DisplayName("A JWT with a null user name is attributed to SystemUser.")
  void shouldUseSystemUserWhenUserNameIsNull() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getUserInformation()).thenReturn(userNamed("sub-id", null));

    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    verify(auditLogDataService)
        .createEntry(
            eq("TestData"),
            eq(data.id()),
            eq("UNKNOWN"),
            eq(AuditAction.INSERT),
            eq(systemUserRef));
  }

  @Test
  @DisplayName(
      "An API-key principal (AuthService returns empty) is attributed to SystemUser — same code "
          + "path as a non-JWT principal, asserted as a separate contract.")
  void shouldUseSystemUserForApiKeyPrincipal() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getUserInformation()).thenReturn(Optional.empty());

    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    verify(auditLogDataService)
        .createEntry(
            eq("TestData"),
            eq(data.id()),
            eq("UNKNOWN"),
            eq(AuditAction.INSERT),
            eq(systemUserRef));
  }

  /**
   * Covers the "JWT references a user that no longer exists locally" case: a deleted user whose
   * still-valid token writes one last event. Attribution falls back to {@link SystemUser} so the
   * event is not dropped.
   */
  @Test
  @DisplayName(
      "A JWT whose sub has no matching local UserEntity falls back to SystemUser — the event is "
          + "still recorded, never dropped.")
  void shouldFallBackToSystemUserWhenLocalUserMissing() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getUserInformation()).thenReturn(userNamed("ghost-sub", "ghost"));
    when(userRepository.findBySub("ghost-sub")).thenReturn(Optional.empty());

    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    verify(auditLogDataService)
        .createEntry(
            eq("TestData"),
            eq(data.id()),
            eq("UNKNOWN"),
            eq(AuditAction.INSERT),
            eq(systemUserRef));
  }

  /**
   * Defensive contract: writing the audit entry must never propagate an exception back to the
   * domain transaction that triggered the event — an audit-DB outage cannot be allowed to take
   * down the request path. Verified by asserting the listener still completed normally and the
   * data service was actually invoked.
   */
  @Test
  @DisplayName(
      "An exception thrown by AuditLogDataService.createEntry is swallowed — a downed audit DB "
          + "must not propagate back into the originating domain transaction.")
  void shouldSwallowAuditWriteFailure() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getUserInformation()).thenReturn(userNamed("alice-sub", "alice"));
    when(userRepository.findBySub("alice-sub")).thenReturn(Optional.of(alice));
    doThrow(new RuntimeException("db down"))
        .when(auditLogDataService)
        .createEntry(any(), any(), any(), any(), any(UserEntity.class));

    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    final ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
    verify(auditLogDataService)
        .createEntry(
            eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), captor.capture());
  }

  /**
   * Pins the recursion guard: an {@code OnObjectCreate&lt;AuditLogDto&gt;} must NOT trigger
   * another {@code createEntry} — otherwise every audit write would loop forever and saturate
   * the database.
   */
  @Test
  @DisplayName(
      "An OnObjectCreate<AuditLogDto> event is ignored — otherwise writing an audit entry would "
          + "trigger another, looping forever.")
  void shouldNotRecursivelyAuditAuditEntries() {
    final UserDto userDto =
        new UserDto(
            alice.id(),
            alice.getName(),
            null,
            null,
            java.time.Instant.now(),
            java.time.Instant.now());
    final AuditLogDto dto =
        new AuditLogDto(
            UUID.randomUUID(),
            "BookDto",
            UUID.randomUUID(),
            "some-book",
            AuditAction.INSERT,
            userDto,
            java.time.Instant.now());

    listener.handleOnObjectCreate(new OnObjectCreate<>(dto));

    verify(auditLogDataService, never())
        .createEntry(any(), any(), any(), any(), any(UserEntity.class));
  }

  /**
   * Successful {@link NameSupplier} resolution: the payload exposes a zero-arg {@code String}
   * method annotated {@code @NameSupplier} returning "Hitchhiker's Guide" — the audit entry's
   * name column is populated from it rather than the {@code "UNKNOWN"} fallback.
   */
  @Test
  @DisplayName(
      "A correctly annotated @NameSupplier method (zero args, returns String) supplies the "
          + "human-readable name on the audit entry.")
  void shouldUseNameFromNameSupplierWhenAnnotatedCorrectly() {
    final NamedData data = new NamedData(UUID.randomUUID(), "Hitchhiker's Guide");
    when(authService.getUserInformation()).thenReturn(userNamed("alice-sub", "alice"));
    when(userRepository.findBySub("alice-sub")).thenReturn(Optional.of(alice));

    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    verify(auditLogDataService)
        .createEntry(
            eq("NamedData"),
            eq(data.id()),
            eq("Hitchhiker's Guide"),
            eq(AuditAction.INSERT),
            eq(alice));
  }

  /**
   * Defensive case for incorrectly used {@code @NameSupplier}: a multi-arg method (cannot be
   * invoked without arguments) and a non-String return type (would not fit the name column).
   * The listener silently falls back to {@code "UNKNOWN"} rather than throwing.
   */
  @Test
  @DisplayName(
      "A @NameSupplier with arguments or non-String return type is rejected silently and the "
          + "name falls back to UNKNOWN — author mistakes must not break audit writes.")
  void shouldFallBackToUnknownWhenNameSupplierUsedIncorrectly() {
    final BadlyNamedData data = new BadlyNamedData(UUID.randomUUID(), "ignored");
    when(authService.getUserInformation()).thenReturn(userNamed("alice-sub", "alice"));
    when(userRepository.findBySub("alice-sub")).thenReturn(Optional.of(alice));

    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    verify(auditLogDataService)
        .createEntry(
            eq("BadlyNamedData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), eq(alice));
  }

  @Test
  @DisplayName(
      "A payload with no @NameSupplier method gets the literal name UNKNOWN — the audit entry is "
          + "still emitted.")
  void shouldFallBackToUnknownWhenNoNameSupplierPresent() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getUserInformation()).thenReturn(userNamed("alice-sub", "alice"));
    when(userRepository.findBySub("alice-sub")).thenReturn(Optional.of(alice));

    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    verify(auditLogDataService)
        .createEntry(
            eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), eq(alice));
  }

  @Test
  @DisplayName(
      "All three event handlers fail fast with NullPointerException when called with a null event.")
  void shouldRejectNullEvent() {
    assertThatThrownBy(() -> listener.handleOnObjectCreate(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> listener.handleOnObjectUpdate(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> listener.handleOnObjectDelete(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName(
      "The constructor rejects null for each of its three collaborators with NullPointerException "
          + "— a misconfigured context surfaces at startup, not at first event.")
  void shouldRejectNullDependencies() {
    assertThatThrownBy(() -> new AuditLogEventListener(null, authService, userRepository))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new AuditLogEventListener(auditLogDataService, null, userRepository))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new AuditLogEventListener(auditLogDataService, authService, null))
        .isInstanceOf(NullPointerException.class);
  }

  private record TestData(UUID id, String value) implements WithId {}

  private record NamedData(UUID id, String title) implements WithId {

    @NameSupplier
    public String displayName() {
      return title;
    }
  }

  private record BadlyNamedData(UUID id, String title) implements WithId {

    @NameSupplier
    public String displayName(final String prefix) {
      return prefix + title;
    }

    @NameSupplier
    public int badReturnType() {
      return 42;
    }
  }
}
