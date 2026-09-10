package com.openelements.spring.base.security;

import com.openelements.spring.base.security.roles.Roles;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Convenience facade over Spring Security's {@link SecurityContextHolder}.
 *
 * <p>The methods on this service are intended to be called from inside a request thread, where
 * Spring Security has populated the {@link Authentication} via the configured filter chain (see
 * {@link SecurityConfig}). Each accessor fails fast with an {@link IllegalStateException} if the
 * expected piece of authentication state is missing or has the wrong shape — these conditions
 * normally indicate a programming error rather than an authentication failure (which would have
 * been rejected earlier in the filter chain).
 *
 * <p><b>One deliberate exception:</b> {@link #getRoles()} never throws because of missing
 * authentication state — it degrades <em>closed</em> to an empty set. It is the only accessor that
 * may legitimately be called from a code path that runs without an authenticated caller (its
 * companion {@link #findAuthentication()} exposes the same absent-state-tolerant read of the
 * context). Do not "align" {@code getRoles()} with the fail-fast accessors: the fail-closed
 * guarantee is intentional and covered by tests.
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * @Service
 * public class NoteService {
 *
 *   private final AuthService authService;
 *
 *   public Note createNote(String body) {
 *     UserInformation user = authService.getUserInformation()
 *         .orElseThrow(() -> new IllegalStateException("Not a JWT request"));
 *     return new Note(user.id(), body);
 *   }
 * }
 * }</pre>
 */
@Service
public class AuthService {

  /**
   * Holder strategy used to look up the current {@link
   * org.springframework.security.core.context.SecurityContext}. Resolved once at construction so
   * the same strategy is consulted on every call — picks up any custom strategy a consuming
   * application has installed via {@link SecurityContextHolder#setStrategyName(String)}, and
   * remains the documented Spring Security 6 way to access the holder.
   */
  private final SecurityContextHolderStrategy securityContextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();

  /** Creates a new service that reads authentication state from the Spring Security context. */
  public AuthService() {}

  /**
   * Returns the {@link Authentication} of the current request.
   *
   * <p>Companion to {@link #findAuthentication()}: this variant fails fast and is the right choice
   * for code that can only run behind an authenticated endpoint, where an absent {@link
   * Authentication} indicates a programming error. Use {@link #findAuthentication()} instead from
   * code that may legitimately run without an authenticated caller.
   *
   * @return the current authentication, never {@code null}
   * @throws IllegalStateException if there is no authentication in the security context
   */
  @NonNull
  public Authentication getAuthentication() {
    return findAuthentication()
        .orElseThrow(() -> new IllegalStateException("No authentication found"));
  }

  /**
   * Returns the {@link Authentication} of the current request, if any.
   *
   * <p>Companion to {@link #getAuthentication()}: use this variant from code that may legitimately
   * run without an authenticated caller (e.g. on a {@code permitAll} path, where an unauthenticated
   * request carries only an anonymous token, or none at all). The two are equal peers — neither is
   * deprecated.
   *
   * @return an {@link Optional} holding the current authentication, or empty if none is bound to the
   *     security context
   */
  @NonNull
  public Optional<Authentication> findAuthentication() {
    return Optional.ofNullable(securityContextHolderStrategy.getContext().getAuthentication());
  }

  /**
   * Returns the role names of the current caller, without Spring Security's {@link
   * Roles#AUTHORITY_PREFIX}.
   *
   * <p>Every authority whose value starts with {@link Roles#AUTHORITY_PREFIX} contributes its
   * prefix-free remainder to the result — so an authority {@code ROLE_IT-ADMIN} becomes the role
   * name {@code IT-ADMIN}, directly comparable against the {@link Roles} constants (e.g. {@code
   * authService.getRoles().contains(Roles.ROLE_IT_ADMIN)}). Authorities that do not carry the prefix
   * — most notably the {@code SCOPE_*} authorities derived from the JWT {@code scope} claim — are
   * excluded, as is a bare or whitespace-only {@code ROLE_} authority. Comparison is
   * case-sensitive, matching Spring Security's own {@code hasRole(...)} evaluation.
   *
   * <p><b>Unlike every other accessor on this class, this method never throws</b> because of missing
   * authentication state: an absent security context, a {@code null} authority collection, or {@code
   * null} entries within it all yield an empty set rather than an exception. It never returns more
   * roles than the caller actually holds.
   *
   * <p><b>Which names, not how they authenticated.</b> The returned set answers <em>which role names
   * the caller carries</em>; it must never be used to infer <em>how</em> the caller authenticated. A
   * JWT whose {@code roles} claim contains {@code API_KEY} or {@code ANONYMOUS} produces exactly that
   * role name, indistinguishable from what {@code ApiKeyAuthenticationFilter} or the anonymous filter
   * produce. The authentication mechanism is answered by the {@link Authentication} type, not by a
   * role name.
   *
   * <p>The result is an <b>immutable snapshot</b> of the current request's roles: mutation attempts
   * throw {@link UnsupportedOperationException}, and {@code contains(null)} throws {@link
   * NullPointerException} (a {@code null} role name is a programming error at the call site, not a
   * security-context state that should degrade closed). Hold it for the duration of the request
   * rather than calling this method once per role check.
   *
   * @return an immutable set of prefix-free role names, empty if the caller holds none; never {@code
   *     null}
   */
  @NonNull
  public Set<String> getRoles() {
    return findAuthentication()
        .map(Authentication::getAuthorities)
        .map(AuthService::toRoleNames)
        .orElseGet(Set::of);
  }

  private static Set<String> toRoleNames(
      final Collection<? extends GrantedAuthority> authorities) {
    if (authorities == null) {
      return Set.of();
    }
    return authorities.stream()
        .filter(Objects::nonNull)
        .map(GrantedAuthority::getAuthority)
        .filter(Objects::nonNull)
        .filter(authority -> authority.startsWith(Roles.AUTHORITY_PREFIX))
        .map(authority -> authority.substring(Roles.AUTHORITY_PREFIX.length()))
        .filter(name -> !name.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Returns the raw principal of the current authentication.
   *
   * <p>The concrete type depends on the authentication mechanism: a {@link Jwt} for bearer-token
   * requests, an {@code ApiKeyEntity} for API-key requests, or a {@link String} for primitive
   * authentication tokens.
   *
   * @return the raw principal, never {@code null}
   * @throws IllegalStateException if no principal is associated with the current authentication
   */
  @NonNull
  public Object getPrincipalObject() {
    final Object principal = getAuthentication().getPrincipal();
    if (principal == null) {
      throw new IllegalStateException("No principal found");
    }
    return principal;
  }

  /**
   * Returns the JWT principal of the current authentication.
   *
   * <p>Use this when the caller is certain the request was authenticated via OAuth2 (e.g. an
   * endpoint that is not reachable through API keys).
   *
   * @return the JWT principal, never {@code null}
   * @throws IllegalStateException if the request was not authenticated via JWT
   */
  @NonNull
  public Jwt getPrincipalJwt() {
    final Object principal = getPrincipalObject();
    if (principal instanceof Jwt jwt) {
      return jwt;
    }
    throw new IllegalStateException("Principal is not a JWT");
  }

  /**
   * Returns a typed view of the user information for the current request, if the request was
   * authenticated via a JWT.
   *
   * <p>For JWT-authenticated requests, claims are extracted from the bearer token: the {@code sub}
   * claim is required and validated; {@code name}, {@code email}, and {@code picture} may be {@code
   * null} depending on the identity provider's configuration. The {@code picture} claim is
   * normalised: an absent or blank value is exposed as {@code null}.
   *
   * <p>For non-JWT principals — typically API-key authenticated requests, where the principal is an
   * {@code ApiKeyEntity} — an empty {@link Optional} is returned. API keys are not currently
   * associated with a specific user, and callers must explicitly opt in to handling that case
   * rather than receiving a sentinel value that could be persisted by accident.
   *
   * @return the user information for the current request, or empty if the request was not
   *     authenticated via a JWT
   * @throws IllegalStateException if the principal is a JWT without a {@code sub} claim
   */
  @NonNull
  public Optional<UserInformation> getUserInformation() {
    final Object principal = getPrincipalObject();
    if (principal instanceof Jwt jwt) {
      final String sub = jwt.getSubject();
      if (sub == null || sub.isBlank()) {
        throw new IllegalStateException("No sub found");
      }
      final String name = jwt.getClaimAsString("name");
      final String email = jwt.getClaimAsString("email");
      final String picture = jwt.getClaimAsString("picture");
      final String avatarUrl = (picture == null || picture.isBlank()) ? null : picture;
      final String preferredUsername = jwt.getClaimAsString("preferred_username");
      final String userName = resolveUserName(preferredUsername, email, sub);
      return Optional.of(new UserInformation(sub, name, email, avatarUrl, userName, sub));
    }
    return Optional.empty();
  }

  /**
   * Applies the {@code preferred_username → email → sub → "user-<UUID>"} fallback chain so that
   * {@link UserInformation#userName()} is always non-null and non-blank.
   *
   * <p>The synthetic UUID fallback is a last-resort guard: in practice it is unreachable because
   * {@code sub} is enforced non-blank above, so the third arm of the chain always succeeds. The
   * guard exists so the contract of {@code UserInformation.userName()} is unconditional.
   */
  private static String resolveUserName(
      final String preferredUsername, final String email, final String sub) {
    if (preferredUsername != null && !preferredUsername.isBlank()) {
      return preferredUsername;
    }
    if (email != null && !email.isBlank()) {
      return email;
    }
    if (sub != null && !sub.isBlank()) {
      return sub;
    }
    return "user-" + UUID.randomUUID();
  }

  /**
   * Returns the principal of the current authentication adapted to {@link AuthenticatedPrincipal}.
   *
   * <p>Provides a uniform abstraction across the supported authentication types:
   *
   * <ul>
   *   <li>An {@link AuthenticatedPrincipal} is returned as-is.
   *   <li>A {@link String} principal is wrapped in an ad-hoc {@link AuthenticatedPrincipal} whose
   *       {@code getName()} returns that string.
   *   <li>A {@link Jwt} principal is wrapped so that {@code getName()} returns the {@code sub}
   *       claim.
   * </ul>
   *
   * @return the principal as an {@link AuthenticatedPrincipal}, never {@code null}
   * @throws IllegalStateException if the principal type is none of the above
   */
  @NonNull
  public AuthenticatedPrincipal getPrincipal() {
    final Object principal = getPrincipalObject();
    if (principal instanceof AuthenticatedPrincipal userPrincipal) {
      return userPrincipal;
    }
    if (principal instanceof String id) {
      return new AuthenticatedPrincipal() {
        @Override
        public String getName() {
          return id;
        }
      };
    }
    if (principal instanceof Jwt jwt) {
      return new AuthenticatedPrincipal() {
        @Override
        public String getName() {
          return jwt.getSubject();
        }
      };
    }
    throw new IllegalStateException("Principal can not be defined");
  }
}
