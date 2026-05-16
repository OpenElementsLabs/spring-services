package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AuditLogDataService finders (unit)")
class AuditLogDataServiceTest {

    private static final Pageable DEFAULT_PAGE = PageRequest.of(0, 20);
    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditLogDataService service =
            new AuditLogDataService(repository, userRepository, mock(ApplicationEventPublisher.class));
    private final UserEntity alice = new UserEntity();
    private final UserEntity bob = new UserEntity();

    @BeforeEach
    void initUsers() {
        alice.setId(UUID.randomUUID());
        alice.setSub("alice-sub");
        alice.setName("alice");
        bob.setId(UUID.randomUUID());
        bob.setSub("bob-sub");
        bob.setName("bob");
    }

    @Test
    void findByEntityTypeShouldDelegateAndMap() {
        final AuditLogEntity entity = new AuditLogEntity();
        entity.setEntityType("BookDto");
        entity.setEntityId(UUID.randomUUID());
        entity.setAction(AuditAction.INSERT);
        entity.setUser(alice);
        when(repository.findByEntityTypeOrderByCreatedAtDesc("BookDto", DEFAULT_PAGE))
                .thenReturn(new PageImpl<>(List.of(entity)));

        final Page<AuditLogDto> result = service.findByEntityType("BookDto", DEFAULT_PAGE);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().entityType()).isEqualTo("BookDto");
        assertThat(result.getContent().getFirst().user().id()).isEqualTo(alice.id());
        assertThat(result.getContent().getFirst().user().name()).isEqualTo("alice");
    }

    @Test
    void findByUserShouldDelegateAndMap() {
        final AuditLogEntity entity = new AuditLogEntity();
        entity.setEntityType("UserDto");
        entity.setEntityId(UUID.randomUUID());
        entity.setAction(AuditAction.UPDATE);
        entity.setUser(bob);
        when(repository.findByUserIdOrderByCreatedAtDesc(bob.id(), DEFAULT_PAGE))
                .thenReturn(new PageImpl<>(List.of(entity)));

        final Page<AuditLogDto> result = service.findByUser(bob.id(), DEFAULT_PAGE);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().action()).isEqualTo(AuditAction.UPDATE);
        assertThat(result.getContent().getFirst().user().id()).isEqualTo(bob.id());
    }

    @Test
    void findByEntityTypeAndUserShouldDelegateAndMap() {
        final AuditLogEntity entity = new AuditLogEntity();
        entity.setEntityType("BookDto");
        entity.setEntityId(UUID.randomUUID());
        entity.setAction(AuditAction.DELETE);
        entity.setUser(alice);
        when(repository.findByEntityTypeAndUserIdOrderByCreatedAtDesc(
                "BookDto", alice.id(), DEFAULT_PAGE))
                .thenReturn(new PageImpl<>(List.of(entity)));

        final Page<AuditLogDto> result =
                service.findByEntityTypeAndUser("BookDto", alice.id(), DEFAULT_PAGE);

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
        assertThatThrownBy(() -> service.findByEntityTypeAndUser(null, alice.id(), DEFAULT_PAGE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.findByEntityTypeAndUser("BookDto", null, DEFAULT_PAGE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void findByDayShouldDelegateWithDayBoundariesAndMap() {
        final LocalDate day = LocalDate.of(2026, 5, 16);
        final ZoneId zone = ZoneId.systemDefault();
        final AuditLogEntity first = new AuditLogEntity();
        first.setEntityType("BookDto");
        first.setEntityId(UUID.randomUUID());
        first.setAction(AuditAction.INSERT);
        first.setUser(alice);
        final AuditLogEntity second = new AuditLogEntity();
        second.setEntityType("BookDto");
        second.setEntityId(UUID.randomUUID());
        second.setAction(AuditAction.UPDATE);
        second.setUser(bob);
        when(repository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                day.atStartOfDay(zone).toInstant(),
                day.plusDays(1).atStartOfDay(zone).toInstant()))
                .thenReturn(List.of(first, second));

        final List<AuditLogDto> result = service.findByDay(day);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).action()).isEqualTo(AuditAction.INSERT);
        assertThat(result.get(0).user().id()).isEqualTo(alice.id());
        assertThat(result.get(1).action()).isEqualTo(AuditAction.UPDATE);
        assertThat(result.get(1).user().id()).isEqualTo(bob.id());
    }

    @Test
    void findByDayShouldReturnEmptyWhenNoMatches() {
        final LocalDate day = LocalDate.of(2026, 5, 16);
        final ZoneId zone = ZoneId.systemDefault();
        when(repository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                day.atStartOfDay(zone).toInstant(),
                day.plusDays(1).atStartOfDay(zone).toInstant()))
                .thenReturn(List.of());

        assertThat(service.findByDay(day)).isEmpty();
    }

    @Test
    void findByDayShouldRejectNull() {
        assertThatThrownBy(() -> service.findByDay(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createEntryShouldRejectNull() {
        assertThatThrownBy(
                () -> service.createEntry(null, UUID.randomUUID(), AuditAction.INSERT, alice))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.createEntry("BookDto", null, AuditAction.INSERT, alice))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.createEntry("BookDto", UUID.randomUUID(), null, alice))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                () ->
                        service.createEntry(
                                "BookDto", UUID.randomUUID(), AuditAction.INSERT, (UserEntity) null))
                .isInstanceOf(NullPointerException.class);
    }
}
