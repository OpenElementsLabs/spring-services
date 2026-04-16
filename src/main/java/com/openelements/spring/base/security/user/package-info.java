/**
 * Local user-profile mirror for the authenticated identities supplied by the OAuth2 identity
 * provider.
 *
 * <p>The platform deliberately does not own credentials — authentication is handled entirely by an
 * external OAuth2 provider via JWT. This package only persists the subset of information about a
 * user that the application itself needs to attach to domain data (display name, email, avatar
 * image), keyed by the JWT's {@code sub} claim.
 *
 * <h2>Lifecycle</h2>
 *
 * <ul>
 *   <li>The {@link com.openelements.spring.base.security.user.UserEntity} for a given JWT subject
 *       is created lazily on first access by {@link
 *       com.openelements.spring.base.security.user.UserService#getCurrentUser()}.
 *   <li>If the JWT's {@code name} or {@code email} claim has changed since the last call, the local
 *       entity is updated transparently.
 *   <li>Avatars (max 2 MB, {@code image/jpeg} or {@code image/png}) are stored as a {@code byte[]}
 *       column on the user row and exposed via {@link com.openelements.spring.base.data.ImageData}.
 * </ul>
 */
package com.openelements.spring.base.security.user;
