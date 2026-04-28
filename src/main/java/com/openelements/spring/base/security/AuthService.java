package com.openelements.spring.base.security;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * @Service
 * public class NoteService {
 *
 *   private final AuthService authService;
 *
 *   public Note createNote(String body) {
 *     UserInformation user = authService.getUserInformation();
 *     return new Note(user.id(), body);
 *   }
 * }
 * }</pre>
 */
@Service
public class AuthService {

    /**
     * Returns the {@link Authentication} of the current request.
     *
     * @return the current authentication, never {@code null}
     * @throws IllegalStateException if there is no authentication in the security context
     */
    @NonNull
    public Authentication getAuthentication() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("No authentication found");
        }
        return authentication;
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
        if (principal == null) {
            throw new IllegalStateException("No principal found");
        }
        if (principal instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("Principal is not a JWT");
    }

    /**
     * Returns a typed view of the user information carried by the JWT of the current request.
     *
     * <p>The {@code sub} claim is required and validated; {@code name}, {@code email}, and {@code
     * avatar} may be {@code null} depending on the identity provider's configuration. The {@code
     * avatar} claim is normalised: an absent or blank value is exposed as {@code null}.
     *
     * @return the user information, never {@code null}
     * @throws IllegalStateException if the request was not authenticated via JWT, or if the JWT does
     *                               not contain a {@code sub} claim
     */
    @NonNull
    public UserInformation getUserInformation() {
        final Jwt jwt = getPrincipalJwt();
        final String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException("No sub found");
        }
        final String pcitureClaim = jwt.getClaimAsString("picture");
        final String avatarUrl = (pcitureClaim == null || pcitureClaim.isBlank()) ? null : pcitureClaim;
        return new UserInformation(
                jwt.getSubject(), jwt.getClaimAsString("name"), jwt.getClaimAsString("email"), avatarUrl);
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
