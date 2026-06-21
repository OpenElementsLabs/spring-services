package com.openelements.spring.base.services.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Mockito-style unit tests for the read-side finder methods of {@link AuditLogDataService}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The argument-and-return contract of each finder: {@code findByEntityType},
 * {@code findByUser}, {@code findByEntityTypeAndUser}, {@code findByDay}, {@code findLatest} and
 * {@code findLatestByEntityType}. Verified concerns are: (1) the right {@link AuditLogRepository}
 * method is called with the right arguments, (2) the day-window finder converts a {@link
 * LocalDate} into a {@code [00:00 systemDefault, +1 day)} half-open instant range, (3) each
 * finder maps the returned {@link AuditLogEntity}s to {@link AuditLogDto}s (including the nested
 * user), (4) every finder rejects null arguments and {@code findLatest*} rejects a non-positive
 * limit. Write-side ({@code createEntry}) is covered only for its null-rejection guard.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 with Mockito and AssertJ. No Spring context — the service is constructed
 * directly with three mocked collaborators.
 *
 * <p><b>Mock-Audit.</b> Three mocks, all justified for a coordination-layer service:
 *
 * <ul>
 *   <li>{@code AuditLogRepository} — stubbing finder return values is the entire point: the
 *       service is responsible for delegating to the correct query method with the correct
 *       arguments and mapping the result. Spinning up Postgres for every permutation would add
 *       no coverage that the integration test ({@link AuditLogIntegrationTest}) does not already
 *       provide.
 *   <li>{@code UserRepository} — held only because the production constructor takes one for the
 *       write path; the finder tests never invoke it. Could plausibly be unused in finder-only
 *       tests if the service were split.
 *   <li>{@code ApplicationEventPublisher} — inherited collaborator from
 *       {@code AbstractDbBackedDataService}; lifecycle events are not exercised here.
 * </ul>
 */
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
  @DisplayName(
      "findByEntityType(type, page) delegates to findByEntityTypeOrderByCreatedAtDesc and maps "
          + "each entity (including the nested user) to a DTO.")
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
  @DisplayName(
      "findByUser(userId, page) delegates to findByUserIdOrderByCreatedAtDesc and maps the result "
          + "page to DTOs.")
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
  @DisplayName(
      "findByEntityTypeAndUser(type, userId, page) delegates to the combined query method and "
          + "maps the result to DTOs.")
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
  @DisplayName("findByEntityType returns an empty page when the repository finds no rows.")
  void findByEntityTypeShouldReturnEmptyWhenNoMatches() {
    when(repository.findByEntityTypeOrderByCreatedAtDesc("NoteDto", DEFAULT_PAGE))
        .thenReturn(Page.empty());

    assertThat(service.findByEntityType("NoteDto", DEFAULT_PAGE)).isEmpty();
  }

  @Test
  @DisplayName(
      "All three paged finders throw NullPointerException for any null argument — fail-fast at "
          + "the boundary instead of surfacing as a confusing JPA error.")
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

  /**
   * Pins the half-open day window: {@code [start-of-day, start-of-next-day)} using the
   * system-default zone. The boundary contract is what callers depend on for "events on this
   * calendar day" — drift to a closed/open range would silently shift entries between days.
   */
  @Test
  @DisplayName(
      "findByDay(day) converts the LocalDate into a [00:00 system-zone, +1 day) half-open instant "
          + "range and maps the resulting entities to DTOs in repository order.")
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
            day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant()))
        .thenReturn(List.of(first, second));

    final List<AuditLogDto> result = service.findByDay(day);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).action()).isEqualTo(AuditAction.INSERT);
    assertThat(result.get(0).user().id()).isEqualTo(alice.id());
    assertThat(result.get(1).action()).isEqualTo(AuditAction.UPDATE);
    assertThat(result.get(1).user().id()).isEqualTo(bob.id());
  }

  @Test
  @DisplayName("findByDay returns an empty list when the repository finds no rows in the window.")
  void findByDayShouldReturnEmptyWhenNoMatches() {
    final LocalDate day = LocalDate.of(2026, 5, 16);
    final ZoneId zone = ZoneId.systemDefault();
    when(repository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant()))
        .thenReturn(List.of());

    assertThat(service.findByDay(day)).isEmpty();
  }

  @Test
  @DisplayName("findByDay(null) throws NullPointerException at the boundary.")
  void findByDayShouldRejectNull() {
    assertThatThrownBy(() -> service.findByDay(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName(
      "findLatest(limit) calls findAllByOrderByCreatedAtDesc with Limit.of(limit) and maps the "
          + "result to DTOs.")
  void findLatestShouldDelegateWithLimitAndMap() {
    final AuditLogEntity first = new AuditLogEntity();
    first.setEntityType("BookDto");
    first.setEntityId(UUID.randomUUID());
    first.setAction(AuditAction.DELETE);
    first.setUser(alice);
    final AuditLogEntity second = new AuditLogEntity();
    second.setEntityType("BookDto");
    second.setEntityId(UUID.randomUUID());
    second.setAction(AuditAction.INSERT);
    second.setUser(bob);
    when(repository.findAllByOrderByCreatedAtDesc(Limit.of(3))).thenReturn(List.of(first, second));

    final List<AuditLogDto> result = service.findLatest(3);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).action()).isEqualTo(AuditAction.DELETE);
    assertThat(result.get(0).user().id()).isEqualTo(alice.id());
    assertThat(result.get(1).action()).isEqualTo(AuditAction.INSERT);
    assertThat(result.get(1).user().id()).isEqualTo(bob.id());
  }

  @Test
  @DisplayName("findLatest returns an empty list when the audit log is empty.")
  void findLatestShouldReturnEmptyWhenNoEntries() {
    when(repository.findAllByOrderByCreatedAtDesc(Limit.of(10))).thenReturn(List.of());

    assertThat(service.findLatest(10)).isEmpty();
  }

  @Test
  @DisplayName(
      "findLatest rejects zero and negative limits with IllegalArgumentException — a non-positive "
          + "limit has no defined semantics.")
  void findLatestShouldRejectNonPositiveLimit() {
    assertThatThrownBy(() -> service.findLatest(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.findLatest(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName(
      "findLatestByEntityType(type, limit) calls findByEntityTypeOrderByCreatedAtDesc with "
          + "Limit.of(limit) and maps the result to DTOs.")
  void findLatestByEntityTypeShouldDelegateWithLimitAndMap() {
    final AuditLogEntity first = new AuditLogEntity();
    first.setEntityType("BookDto");
    first.setEntityId(UUID.randomUUID());
    first.setAction(AuditAction.UPDATE);
    first.setUser(alice);
    final AuditLogEntity second = new AuditLogEntity();
    second.setEntityType("BookDto");
    second.setEntityId(UUID.randomUUID());
    second.setAction(AuditAction.INSERT);
    second.setUser(bob);
    when(repository.findByEntityTypeOrderByCreatedAtDesc("BookDto", Limit.of(5)))
        .thenReturn(List.of(first, second));

    final List<AuditLogDto> result = service.findLatestByEntityType("BookDto", 5);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).entityType()).isEqualTo("BookDto");
    assertThat(result.get(0).action()).isEqualTo(AuditAction.UPDATE);
    assertThat(result.get(0).user().id()).isEqualTo(alice.id());
    assertThat(result.get(1).action()).isEqualTo(AuditAction.INSERT);
    assertThat(result.get(1).user().id()).isEqualTo(bob.id());
  }

  @Test
  @DisplayName("findLatestByEntityType returns an empty list when no rows match the entity type.")
  void findLatestByEntityTypeShouldReturnEmptyWhenNoMatches() {
    when(repository.findByEntityTypeOrderByCreatedAtDesc("NoteDto", Limit.of(10)))
        .thenReturn(List.of());

    assertThat(service.findLatestByEntityType("NoteDto", 10)).isEmpty();
  }

  @Test
  @DisplayName("findLatestByEntityType(null, ...) throws NullPointerException at the boundary.")
  void findLatestByEntityTypeShouldRejectNullType() {
    assertThatThrownBy(() -> service.findLatestByEntityType(null, 5))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName(
      "findLatestByEntityType rejects zero and negative limits with IllegalArgumentException.")
  void findLatestByEntityTypeShouldRejectNonPositiveLimit() {
    assertThatThrownBy(() -> service.findLatestByEntityType("BookDto", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.findLatestByEntityType("BookDto", -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName(
      "createEntry(...) rejects null for every one of its five arguments (entityType, entityId, "
          + "name, action, user) with NullPointerException.")
  void createEntryShouldRejectNull() {
    assertThatThrownBy(
            () -> service.createEntry(null, UUID.randomUUID(), "name", AuditAction.INSERT, alice))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> service.createEntry("BookDto", null, "name", AuditAction.INSERT, alice))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                service.createEntry("BookDto", UUID.randomUUID(), null, AuditAction.INSERT, alice))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.createEntry("BookDto", UUID.randomUUID(), "name", null, alice))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                service.createEntry(
                    "BookDto", UUID.randomUUID(), "name", AuditAction.INSERT, (UserEntity) null))
        .isInstanceOf(NullPointerException.class);
  }
}
