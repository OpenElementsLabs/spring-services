package com.openelements.spring.base.services.audit;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    private static UserInformation userNamed(final String sub, final String name) {
        return new UserInformation(sub, name, "test@example.com", null);
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
    void shouldRecordCreateEntryWithAuthenticatedUser() {
        final TestData data = new TestData(UUID.randomUUID(), "x");
        when(authService.getUserInformation()).thenReturn(userNamed("alice-sub", "alice"));
        when(userRepository.findBySub("alice-sub")).thenReturn(Optional.of(alice));

        listener.handleOnObjectCreate(new OnObjectCreate<>(data));

        verify(auditLogDataService)
                .createEntry(eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), eq(alice));
    }

    @Test
    void shouldRecordUpdateEntry() {
        final TestData data = new TestData(UUID.randomUUID(), "x");
        when(authService.getUserInformation()).thenReturn(userNamed("bob-sub", "bob"));
        when(userRepository.findBySub("bob-sub")).thenReturn(Optional.of(bob));

        listener.handleOnObjectUpdate(new OnObjectUpdate<>(data));

        verify(auditLogDataService)
                .createEntry(eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.UPDATE), eq(bob));
    }

    @Test
    void shouldRecordDeleteEntry() {
        final TestData data = new TestData(UUID.randomUUID(), "x");
        when(authService.getUserInformation()).thenReturn(userNamed("alice-sub", "alice"));
        when(userRepository.findBySub("alice-sub")).thenReturn(Optional.of(alice));

        listener.handleOnObjectDelete(new OnObjectDelete<>(data));

        verify(auditLogDataService)
                .createEntry(eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.DELETE), eq(alice));
    }

    @Test
    void shouldUseSystemUserWhenNoAuthentication() {
        final TestData data = new TestData(UUID.randomUUID(), "x");
        when(authService.getUserInformation())
                .thenThrow(new IllegalStateException("No authentication"));

        listener.handleOnObjectCreate(new OnObjectCreate<>(data));

        verify(auditLogDataService)
                .createEntry(eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), eq(systemUserRef));
    }

    @Test
    void shouldUseSystemUserWhenUserInformationIsNull() {
        final TestData data = new TestData(UUID.randomUUID(), "x");
        when(authService.getUserInformation()).thenReturn(null);

        listener.handleOnObjectCreate(new OnObjectCreate<>(data));

        verify(auditLogDataService)
                .createEntry(eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), eq(systemUserRef));
    }

    @Test
    void shouldUseSystemUserWhenUserNameIsBlank() {
        final TestData data = new TestData(UUID.randomUUID(), "x");
        when(authService.getUserInformation()).thenReturn(userNamed("sub-id", "   "));

        listener.handleOnObjectCreate(new OnObjectCreate<>(data));

        verify(auditLogDataService)
                .createEntry(eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), eq(systemUserRef));
    }

    @Test
    void shouldUseSystemUserWhenUserNameIsNull() {
        final TestData data = new TestData(UUID.randomUUID(), "x");
        when(authService.getUserInformation()).thenReturn(userNamed("sub-id", null));

        listener.handleOnObjectCreate(new OnObjectCreate<>(data));

        verify(auditLogDataService)
                .createEntry(eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), eq(systemUserRef));
    }

    @Test
    void shouldUseSystemUserForApiKeyPrincipal() {
        final TestData data = new TestData(UUID.randomUUID(), "x");
        when(authService.getUserInformation())
                .thenReturn(new UserInformation("UNKNOWN", "UNKNOWN", null, null));

        listener.handleOnObjectCreate(new OnObjectCreate<>(data));

        verify(auditLogDataService)
                .createEntry(eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), eq(systemUserRef));
    }

    @Test
    void shouldFallBackToSystemUserWhenLocalUserMissing() {
        final TestData data = new TestData(UUID.randomUUID(), "x");
        when(authService.getUserInformation()).thenReturn(userNamed("ghost-sub", "ghost"));
        when(userRepository.findBySub("ghost-sub")).thenReturn(Optional.empty());

        listener.handleOnObjectCreate(new OnObjectCreate<>(data));

        verify(auditLogDataService)
                .createEntry(eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), eq(systemUserRef));
    }

    @Test
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
                .createEntry(eq("TestData"), eq(data.id()), eq("UNKNOWN"), eq(AuditAction.INSERT), captor.capture());
    }

    @Test
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

    @Test
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

    @Test
    void shouldFallBackToUnknownWhenNameSupplierUsedIncorrectly() {
        final BadlyNamedData data = new BadlyNamedData(UUID.randomUUID(), "ignored");
        when(authService.getUserInformation()).thenReturn(userNamed("alice-sub", "alice"));
        when(userRepository.findBySub("alice-sub")).thenReturn(Optional.of(alice));

        listener.handleOnObjectCreate(new OnObjectCreate<>(data));

        verify(auditLogDataService)
                .createEntry(
                        eq("BadlyNamedData"),
                        eq(data.id()),
                        eq("UNKNOWN"),
                        eq(AuditAction.INSERT),
                        eq(alice));
    }

    @Test
    void shouldFallBackToUnknownWhenNoNameSupplierPresent() {
        final TestData data = new TestData(UUID.randomUUID(), "x");
        when(authService.getUserInformation()).thenReturn(userNamed("alice-sub", "alice"));
        when(userRepository.findBySub("alice-sub")).thenReturn(Optional.of(alice));

        listener.handleOnObjectCreate(new OnObjectCreate<>(data));

        verify(auditLogDataService)
                .createEntry(
                        eq("TestData"),
                        eq(data.id()),
                        eq("UNKNOWN"),
                        eq(AuditAction.INSERT),
                        eq(alice));
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
        assertThatThrownBy(() -> new AuditLogEventListener(null, authService, userRepository))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditLogEventListener(auditLogDataService, null, userRepository))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditLogEventListener(auditLogDataService, authService, null))
                .isInstanceOf(NullPointerException.class);
    }

    private record TestData(UUID id, String value) implements WithId {
    }

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
