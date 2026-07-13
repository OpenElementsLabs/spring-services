package com.openelements.spring.base.services.apitoken;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NonNull;

/**
 * Port for resolving the <em>live</em> authorization state of a user-bound principal.
 *
 * <p>This is the seam between opaque-token validation (planned for spec 010) and the local source
 * of truth for user identity. Opaque user tokens carry only the {@code subjectRef} (the IdP-side
 * identifier of the user); everything that may change without the user's involvement — active
 * status, group memberships, roles — is resolved <em>here</em> at every validation, never frozen
 * into the token.
 *
 * <p><b>Temporary location.</b> This interface is shipped by spec 012 as scaffolding so that the
 * default implementation ({@link
 * com.openelements.spring.base.services.user.UserEntityPrincipalDirectory}) has a contract to
 * implement before the full opaque-token module (spec 010) lands. Spec 010 will become the
 * permanent owner of this port. Consumers who write their own implementation today should expect
 * a one-line package change when 010 merges; the interface shape itself will not change.
 *
 * <p><b>Backing stores.</b> The default implementation is backed by {@code UserEntity} (the SCIM-
 * /JIT-fed local mirror). High-assurance deployments may swap in an implementation that calls the
 * IdP live, or one that adds a short-TTL cache in front of {@code UserEntity}. The contract is
 * identical.
 *
 * <p>Implementations must be thread-safe.
 */
public interface PrincipalDirectory {

  /**
   * Resolves the current authorization state for a user reference.
   *
   * @param subjectRef the IdP-side identifier stored in the token (typically the user's
   *     {@code externalId}, which mirrors the OIDC {@code sub} for IdPs that use the same value
   *     for both)
   * @return the resolved principal, or {@link Optional#empty()} if no such user exists
   */
  @NonNull
  Optional<ResolvedPrincipal> resolveUser(@NonNull String subjectRef);

  /**
   * The live authorization snapshot for a user at validation time.
   *
   * @param subjectRef the IdP-side identifier
   * @param active whether the user is currently active; an inactive user must fail authentication
   *     regardless of any otherwise-valid token
   * @param roles the user's current roles (raw names, without any {@code ROLE_} prefix)
   * @param groups the user's current group memberships (raw names)
   * @param displayName a human-readable name for principal display purposes
   */
  record ResolvedPrincipal(
      @NonNull String subjectRef,
      boolean active,
      @NonNull Set<String> roles,
      @NonNull Set<String> groups,
      @NonNull String displayName) {

    /** Validates components and defensively copies the collections. */
    public ResolvedPrincipal {
      Objects.requireNonNull(subjectRef, "subjectRef must not be null");
      Objects.requireNonNull(displayName, "displayName must not be null");
      roles = Set.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
      groups = Set.copyOf(Objects.requireNonNull(groups, "groups must not be null"));
    }
  }
}
