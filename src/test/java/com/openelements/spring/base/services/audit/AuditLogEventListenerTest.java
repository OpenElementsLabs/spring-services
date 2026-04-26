package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.data.WithId;
import com.openelements.spring.base.events.OnObjectCreate;
import com.openelements.spring.base.events.OnObjectDelete;
import com.openelements.spring.base.events.OnObjectUpdate;
import com.openelements.spring.base.security.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticatedPrincipal;

@DisplayName("AuditLogEventListener")
class AuditLogEventListenerTest {

  private record TestData(UUID id, String value) implements WithId {}

  private final AuditLogDataService auditLogDataService = mock(AuditLogDataService.class);
  private final AuthService authService = mock(AuthService.class);
  private final AuditLogEventListener listener =
      new AuditLogEventListener(auditLogDataService, authService);

  @Test
  void shouldRecordCreateEntryWithAuthenticatedUser() {
    // GIVEN
    final TestData data = new TestData(UUID.randomUUID(), "x");
    final OnObjectCreate<TestData> event = new OnObjectCreate<>(data);
    when(authService.getPrincipal()).thenReturn(principalNamed("alice"));

    // WHEN
    listener.handleOnObjectCreate(event);

    // THEN
    verify(auditLogDataService).createEntry("TestData", data.id(), AuditAction.INSERT, "alice");
  }

  @Test
  void shouldRecordUpdateEntry() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getPrincipal()).thenReturn(principalNamed("bob"));

    listener.handleOnObjectUpdate(new OnObjectUpdate<>(data));

    verify(auditLogDataService).createEntry("TestData", data.id(), AuditAction.UPDATE, "bob");
  }

  @Test
  void shouldRecordDeleteEntry() {
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getPrincipal()).thenReturn(principalNamed("alice"));

    listener.handleOnObjectDelete(new OnObjectDelete<>(data));

    verify(auditLogDataService).createEntry("TestData", data.id(), AuditAction.DELETE, "alice");
  }

  @Test
  void shouldUseSystemUserWhenNoAuthentication() {
    // GIVEN
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getPrincipal()).thenThrow(new IllegalStateException("No authentication"));

    // WHEN
    listener.handleOnObjectCreate(new OnObjectCreate<>(data));

    // THEN
    verify(auditLogDataService)
        .createEntry(eq("TestData"), eq(data.id()), eq(AuditAction.INSERT), eq("System"));
  }

  @Test
  void shouldSwallowAuditWriteFailure() {
    // GIVEN
    final TestData data = new TestData(UUID.randomUUID(), "x");
    when(authService.getPrincipal()).thenReturn(principalNamed("alice"));
    doThrow(new RuntimeException("db down"))
        .when(auditLogDataService)
        .createEntry(any(), any(), any(), any());

    // WHEN / THEN — must not propagate
    listener.handleOnObjectCreate(new OnObjectCreate<>(data));
  }

  @Test
  void shouldNotRecursivelyAuditAuditEntries() {
    // GIVEN
    final AuditLogDto dto =
        new AuditLogDto(
            UUID.randomUUID(),
            "BookDto",
            UUID.randomUUID(),
            AuditAction.INSERT,
            "alice",
            java.time.Instant.now());

    // WHEN
    listener.handleOnObjectCreate(new OnObjectCreate<>(dto));

    // THEN
    verify(auditLogDataService, never()).createEntry(any(), any(), any(), any());
  }

  @Test
  void shouldRejectNullEvent() {
    assertThatThrownBy(() -> listener.handleOnObjectCreate(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> listener.handleOnObjectUpdate(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> listener.handleOnObjectDelete(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectNullDependencies() {
    assertThatThrownBy(() -> new AuditLogEventListener(null, authService))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new AuditLogEventListener(auditLogDataService, null))
        .isInstanceOf(NullPointerException.class);
  }

  private static AuthenticatedPrincipal principalNamed(final String name) {
    return () -> name;
  }
}
