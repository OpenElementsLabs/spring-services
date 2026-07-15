package com.openelements.spring.base.services.user;

import com.openelements.spring.base.services.apitoken.PrincipalDirectory;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link PrincipalDirectory} implementation, backed by the local {@link UserEntity}
 * mirror.
 *
 * <p>Resolves a user's <em>live</em> authorization state from the database row for every opaque
 * token validation. The bridge is intentionally minimal in spec 012:
 *
 * <ul>
 *   <li>{@link UserEntity#isActive()} flows directly into {@link
 *       PrincipalDirectory.ResolvedPrincipal#active()}.
 *   <li>Roles and groups return empty sets. Group/role modelling is not part of this spec's data
 *       model — the SCIM-Provider follow-up extends this implementation to populate them.
 *   <li>{@code subjectRef} is correlated against {@link UserEntity#getExternalId()} (not {@code
 *       sub}). The opaque-token module writes the user's {@code externalId} into the token at
 *       issuance, and SCIM uses {@code externalId} as the canonical IdP identifier.
 * </ul>
 *
 * <p>The directory deliberately does not filter inactive users out at this layer — it returns
 * what it sees. The opaque-token introspector decides what to do with {@code active == false}
 * (typically {@code BadOpaqueTokenException → 401 Unauthorized}).
 *
 * <p>The lookup runs in a read-only transaction so callers (the {@code OpaqueTokenIntrospector})
 * do not need their own transaction boundary.
 */
@Component
public class UserEntityPrincipalDirectory implements PrincipalDirectory {

  private final UserRepository userRepository;

  /**
   * Creates a new directory backed by the user repository.
   *
   * @param userRepository the repository used to look up and live-check users
   */
  public UserEntityPrincipalDirectory(final UserRepository userRepository) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
  }

  @Override
  @Transactional(readOnly = true)
  public @NonNull Optional<ResolvedPrincipal> resolveUser(@NonNull final String subjectRef) {
    Objects.requireNonNull(subjectRef, "subjectRef must not be null");
    return userRepository
        .findByExternalId(subjectRef)
        .map(
            user ->
                new ResolvedPrincipal(
                    user.getExternalId(),
                    user.isActive(),
                    Set.of(),
                    Set.of(),
                    user.getName()));
  }
}
