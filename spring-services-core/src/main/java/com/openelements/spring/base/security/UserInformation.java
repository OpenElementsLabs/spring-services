package com.openelements.spring.base.security;

/**
 * Lightweight, immutable view of the authenticated user, derived from the JWT carried by the
 * current request.
 *
 * <p>Produced by {@link AuthService#getUserInformation()}. The fields map to standard OpenID
 * Connect claims:
 *
 * <ul>
 *   <li>{@code id} — the JWT {@code sub} claim, the stable subject identifier of the user. Never
 *       {@code null} or blank when returned by {@code AuthService}.
 *   <li>{@code name} — the {@code name} claim, typically the user's display name. May be {@code
 *       null} if the identity provider does not include it.
 *   <li>{@code email} — the {@code email} claim. May be {@code null} if not asserted by the
 *       identity provider or if the {@code email} scope was not granted.
 *   <li>{@code avatarUrl} — the {@code avatar} claim, an absolute URL pointing at the identity
 *       provider's avatar image for the user. May be {@code null} if the claim is absent or blank.
 *   <li>{@code userName} — the {@code preferred_username} claim with a fallback chain
 *       ({@code preferred_username → email → sub → "user-<UUID>"}) applied by {@code AuthService}.
 *       Guaranteed non-null and non-blank for {@code AuthService}-produced records, so {@link
 *       com.openelements.spring.base.services.user.UserEntity#getUserName()} can be populated
 *       unconditionally on JIT login.
 *   <li>{@code externalId} — the stable IdP-side identifier. Mirrors {@code id} (the JWT
 *       {@code sub}) for IdPs that use the same value for OIDC {@code sub} and SCIM
 *       {@code externalId} — which covers Authentik, Okta, Entra ID, and Keycloak in default
 *       configurations.
 * </ul>
 *
 * @param id the JWT {@code sub} claim, the stable subject identifier of the user
 * @param name the {@code name} claim, typically the user's display name, or {@code null} if absent
 * @param email the {@code email} claim, or {@code null} if not asserted by the identity provider
 * @param avatarUrl an absolute URL to the identity provider's avatar image, or {@code null} if absent
 * @param userName the resolved preferred username, guaranteed non-null and non-blank for
 *     {@code AuthService}-produced records
 * @param externalId the stable IdP-side identifier, mirroring {@code id} for common IdPs
 */
public record UserInformation(
    String id,
    String name,
    String email,
    String avatarUrl,
    String userName,
    String externalId) {}
