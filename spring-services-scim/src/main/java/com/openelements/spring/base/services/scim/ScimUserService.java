package com.openelements.spring.base.services.scim;

import com.openelements.spring.base.services.audit.AuditAction;
import com.openelements.spring.base.services.audit.AuditLogDataService;
import com.openelements.spring.base.services.scim.model.ScimEmail;
import com.openelements.spring.base.services.scim.model.ScimListResponse;
import com.openelements.spring.base.services.scim.model.ScimMeta;
import com.openelements.spring.base.services.scim.model.ScimName;
import com.openelements.spring.base.services.scim.model.ScimSchemas;
import com.openelements.spring.base.services.scim.model.ScimUser;
import com.openelements.spring.base.services.user.SystemUser;
import com.openelements.spring.base.services.user.UserEntity;
import com.openelements.spring.base.services.user.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for the SCIM Users resource: maps between the SCIM {@code User} representation and
 * the library's {@link UserEntity}, enforces the uniqueness/soft-delete rules from the design, and
 * records each write in the audit log attributed to the reserved SCIM service principal.
 *
 * <p>SCIM never reads or writes {@code sub}; a SCIM-provisioned row stays {@code sub = NULL} until
 * the human first logs in interactively, at which point spec 012's JIT correlation adopts the row.
 */
@Service
public class ScimUserService {

  /** {@code filter} grammar supported by this slice: {@code (userName|externalId) eq "value"}. */
  private static final Pattern EQ_FILTER =
      Pattern.compile("\\s*(userName|externalId)\\s+eq\\s+\"([^\"]*)\"\\s*");

  private static final Set<UUID> RESERVED = Set.of(SystemUser.ID, ScimServicePrincipal.ID);

  private static final String AUDIT_TYPE = "ScimUser";

  private final UserRepository userRepository;

  private final AuditLogDataService auditLogDataService;

  /**
   * Creates the service.
   *
   * @param userRepository the repository backing SCIM users
   * @param auditLogDataService the audit log used to record SCIM writes
   */
  public ScimUserService(
      final UserRepository userRepository, final AuditLogDataService auditLogDataService) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    this.auditLogDataService =
        Objects.requireNonNull(auditLogDataService, "auditLogDataService must not be null");
  }

  /**
   * Creates a new user from a SCIM {@code POST} payload.
   *
   * @param request the SCIM user to create
   * @return the created user as a SCIM representation
   * @throws ScimValidationException if {@code userName} is missing
   * @throws ScimUniquenessException if a row already exists with the same {@code externalId} or
   *     {@code userName}
   */
  @Transactional
  public ScimUser create(final ScimUser request) {
    final String userName = requireUserName(request);
    if (request.externalId() != null
        && userRepository.findByExternalId(request.externalId()).isPresent()) {
      throw new ScimUniquenessException(
          "A user with externalId '" + request.externalId() + "' already exists");
    }
    if (userRepository.findByUserName(userName).isPresent()) {
      throw new ScimUniquenessException("A user with userName '" + userName + "' already exists");
    }
    final UserEntity entity = new UserEntity();
    entity.setUserName(userName);
    entity.setExternalId(request.externalId());
    entity.setName(resolveName(request, userName));
    entity.setEmail(resolvePrimaryEmail(request));
    entity.setActive(request.active() == null || request.active());
    entity.setDeleted(false);
    // sub is intentionally left NULL — SCIM never writes it.
    final UserEntity saved = userRepository.save(entity);
    audit(saved, AuditAction.INSERT);
    return toScim(saved);
  }

  /**
   * Returns a single user by id, treating a soft-deleted row as absent.
   *
   * @param id the user id
   * @return the user as a SCIM representation
   * @throws ScimNotFoundException if no active (non-deleted) row has that id
   */
  @Transactional(readOnly = true)
  public ScimUser getById(final UUID id) {
    final UserEntity entity =
        userRepository
            .findById(id)
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new ScimNotFoundException("User " + id + " not found"));
    return toScim(entity);
  }

  /**
   * Lists users, optionally filtered by {@code userName eq} / {@code externalId eq}, excluding
   * soft-deleted rows and the reserved System and SCIM principals, with 1-based pagination.
   *
   * @param filter the SCIM filter expression, or {@code null}/blank for no filter
   * @param startIndex the 1-based index of the first result (values below 1 are treated as 1)
   * @param count the maximum number of results to return
   * @return a SCIM {@code ListResponse}
   * @throws ScimValidationException if a non-empty filter is not a supported {@code eq} expression
   */
  @Transactional(readOnly = true)
  public ScimListResponse<ScimUser> list(final String filter, final int startIndex, final int count) {
    final List<UserEntity> matches =
        applyFilter(filter).stream()
            .filter(u -> !u.isDeleted())
            .filter(u -> !RESERVED.contains(u.id()))
            .sorted(Comparator.comparing(UserEntity::id))
            .toList();
    final int effectiveStart = Math.max(startIndex, 1);
    final int fromIndex = Math.min(effectiveStart - 1, matches.size());
    final int toIndex = Math.min(fromIndex + Math.max(count, 0), matches.size());
    final List<ScimUser> page = matches.subList(fromIndex, toIndex).stream().map(this::toScim).toList();
    return ScimListResponse.of(page, matches.size(), effectiveStart);
  }

  /**
   * Full-replace update of a user's mutable fields (RFC 7644 §3.5.1). A {@code PUT} carrying
   * {@code active:true} on a soft-deleted row undeletes it. {@code sub} is never written.
   *
   * @param id the user id
   * @param request the replacement SCIM representation
   * @return the updated user as a SCIM representation
   * @throws ScimNotFoundException if no row has that id
   * @throws ScimValidationException if {@code userName} is missing
   */
  @Transactional
  public ScimUser replace(final UUID id, final ScimUser request) {
    final UserEntity entity =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ScimNotFoundException("User " + id + " not found"));
    final String userName = requireUserName(request);
    final boolean active = request.active() == null || request.active();
    entity.setUserName(userName);
    if (request.externalId() != null) {
      entity.setExternalId(request.externalId());
    }
    entity.setName(resolveName(request, userName));
    entity.setEmail(resolvePrimaryEmail(request));
    entity.setActive(active);
    if (entity.isDeleted() && active) {
      entity.setDeleted(false);
      entity.setDeletedAt(null);
    }
    final UserEntity saved = userRepository.save(entity);
    audit(saved, AuditAction.UPDATE);
    return toScim(saved);
  }

  /**
   * Soft-deletes a user: sets {@code active = false}, {@code deleted = true}, {@code deleted_at =
   * now}. The row is retained for referential integrity; a {@code DELETE} audit action is recorded.
   *
   * @param id the user id
   * @throws ScimNotFoundException if no non-deleted row has that id
   */
  @Transactional
  public void softDelete(final UUID id) {
    final UserEntity entity =
        userRepository
            .findById(id)
            .filter(u -> !u.isDeleted())
            .orElseThrow(() -> new ScimNotFoundException("User " + id + " not found"));
    entity.setActive(false);
    entity.setDeleted(true);
    entity.setDeletedAt(Instant.now());
    final UserEntity saved = userRepository.save(entity);
    audit(saved, AuditAction.DELETE);
  }

  private List<UserEntity> applyFilter(final String filter) {
    if (filter == null || filter.isBlank()) {
      return userRepository.findAll();
    }
    final Matcher matcher = EQ_FILTER.matcher(filter);
    if (!matcher.matches()) {
      throw new ScimValidationException("Unsupported filter: " + filter);
    }
    final String attribute = matcher.group(1);
    final String value = matcher.group(2);
    final Optional<UserEntity> hit =
        "userName".equals(attribute)
            ? userRepository.findByUserName(value)
            : userRepository.findByExternalId(value);
    return hit.map(List::of).orElseGet(List::of);
  }

  private void audit(final UserEntity user, final AuditAction action) {
    auditLogDataService.createEntry(
        AUDIT_TYPE,
        user.id(),
        user.getUserName(),
        action,
        userRepository.getReferenceById(ScimServicePrincipal.ID));
  }

  private static String requireUserName(final ScimUser request) {
    final String userName = request.userName();
    if (userName == null || userName.isBlank()) {
      throw new ScimValidationException("userName is required");
    }
    return userName;
  }

  private static String resolveName(final ScimUser request, final String fallback) {
    if (request.name() != null && request.name().formatted() != null
        && !request.name().formatted().isBlank()) {
      return request.name().formatted();
    }
    if (request.displayName() != null && !request.displayName().isBlank()) {
      return request.displayName();
    }
    return fallback;
  }

  private static String resolvePrimaryEmail(final ScimUser request) {
    if (request.emails() == null || request.emails().isEmpty()) {
      return null;
    }
    return request.emails().stream()
        .filter(e -> Boolean.TRUE.equals(e.primary()))
        .map(ScimEmail::value)
        .findFirst()
        .orElseGet(() -> request.emails().get(0).value());
  }

  private ScimUser toScim(final UserEntity entity) {
    final String id = entity.id().toString();
    final List<ScimEmail> emails =
        entity.getEmail() == null
            ? null
            : List.of(new ScimEmail(entity.getEmail(), true, null));
    return new ScimUser(
        List.of(ScimSchemas.USER),
        id,
        entity.getExternalId(),
        entity.getUserName(),
        new ScimName(entity.getName(), null, null),
        entity.getName(),
        emails,
        entity.isActive(),
        new ScimMeta("User", "/scim/v2/Users/" + id, null));
  }
}
