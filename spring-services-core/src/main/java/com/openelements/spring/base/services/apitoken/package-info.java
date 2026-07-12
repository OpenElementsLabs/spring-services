/**
 * Scaffolding for the API token module (spec 010).
 *
 * <p>This package currently contains only the {@link
 * com.openelements.spring.base.services.apitoken.PrincipalDirectory} port, shipped by spec 012 so
 * that the default implementation ({@code UserEntityPrincipalDirectory} in {@code services/user})
 * has a stable contract before spec 010 lands. When spec 010 (the full opaque-token module)
 * merges, the port is preserved in place and joined by the remaining types ({@code
 * ApiTokenEntity}, {@code ApiTokenService}, {@code ApiTokenIntrospector}, …).
 */
package com.openelements.spring.base.services.apitoken;
