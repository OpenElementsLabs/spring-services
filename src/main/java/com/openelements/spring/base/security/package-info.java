/**
 * Authentication and authorization layer of the {@code spring-services} platform.
 *
 * <p>This package wires Spring Security so that every HTTP endpoint exposed by an application built
 * on the platform is protected by default. Two strictly isolated {@link
 * org.springframework.security.web.SecurityFilterChain SecurityFilterChain}s are configured, each
 * dedicated to a single authentication mechanism.
 *
 * <h2>Two-chain model</h2>
 *
 * <p>The {@link com.openelements.spring.base.security.SecurityConfig} configuration contributes two
 * filter-chain beans:
 *
 * <ol>
 *   <li><b>External API chain</b> ({@code @Order(1)}, {@link
 *       com.openelements.spring.base.security.SecurityConfig#externalApiFilterChain}) — scoped to
 *       {@code /api/external/**}. API-key authentication only. The chain authorizes only the
 *       read-only HTTP methods {@code GET}, {@code HEAD} and {@code OPTIONS}; all other methods are
 *       rejected with {@code 403 Forbidden}. JWT bearer tokens are <b>not</b> evaluated on this
 *       chain.
 *   <li><b>Default chain</b> ({@code @Order(2)}, {@link
 *       com.openelements.spring.base.security.SecurityConfig#defaultFilterChain}) — matches every
 *       other request. JWT/OAuth2 resource-server authentication only. The {@code X-API-Key} header
 *       is silently ignored because the {@link
 *       com.openelements.spring.base.security.apikey.ApiKeyAuthenticationFilter} is not registered
 *       on this chain.
 * </ol>
 *
 * <p>This split makes the security model explicit: a request authenticated via API key cannot reach
 * a non-{@code /api/external/**} endpoint, and a JWT cannot authenticate against {@code
 * /api/external/**}. Read-only enforcement for API keys is declarative at the chain level rather
 * than imperative inside the filter.
 *
 * <h2>Anonymous access</h2>
 *
 * <p>The default chain permits anonymous access to two categories of endpoints:
 *
 * <ul>
 *   <li>{@code /api/health/**} — for liveness/readiness probes.
 *   <li>{@code /swagger-ui.html}, {@code /swagger-ui/**} and {@code /v3/api-docs/**} — for the
 *       OpenAPI documentation UI.
 * </ul>
 *
 * <p>CSRF protection is disabled on both chains: the platform exposes a stateless REST API consumed
 * by external clients (browsers using bearer tokens, scripts using API keys, server-to-server
 * calls). No HTTP session is used.
 *
 * <h2>Authentication mechanisms</h2>
 *
 * <h3>1. {@code X-API-Key} header (read-only access on the external API chain)</h3>
 *
 * <p>The {@link com.openelements.spring.base.security.apikey.ApiKeyAuthenticationFilter} is added
 * to the external API chain ahead of {@link
 * org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter}.
 * It is intentionally <em>not</em> annotated with {@code @Component} — instantiating it via a
 * {@code @Bean} method ensures Spring Boot does not auto-register it on every chain.
 *
 * <ul>
 *   <li>If the header is <b>absent</b>, the filter passes the request through. Chain-level
 *       authorization rules then reject the request as unauthenticated ({@code 401}).
 *   <li>If the header is <b>present</b>, the raw key is hashed with SHA-256 and looked up in the
 *       database via {@link
 *       com.openelements.spring.base.services.apikey.ApiKeyDataService#authenticate(String)}. Only
 *       the hash is stored on disk, never the raw key.
 *       <ul>
 *         <li>Unknown key → {@code 401 Unauthorized} (with a JSON error body) and the request is
 *             aborted.
 *         <li>Known key → an {@code ApiKeyAuthentication} token with the {@code ROLE_API_KEY}
 *             authority is placed into the {@link
 *             org.springframework.security.core.context.SecurityContextHolder}, and the request
 *             continues. The chain's {@code authorizeHttpRequests} configuration then enforces the
 *             read-only HTTP-method restriction.
 *       </ul>
 * </ul>
 *
 * <p>Keys are issued through {@link
 * com.openelements.spring.base.services.apikey.ApiKeyDataService#create(
 * com.openelements.spring.base.services.apikey.ApiKeyCreateDto)}: a cryptographically strong random
 * value (prefix {@code crm_} plus 48 random alphanumeric characters generated via {@link
 * java.security.SecureRandom}) is returned to the caller exactly once. Only its SHA-256 hash and a
 * short display prefix (e.g. {@code crm_AbCd...wXyZ}) are persisted, so a leaked database snapshot
 * does not expose usable credentials.
 *
 * <h3>2. OAuth2 / JWT bearer token (full access on the default chain)</h3>
 *
 * <p>The default chain configures Spring Security's standard OAuth2 resource-server filter. The
 * token must be supplied in the {@code Authorization: Bearer <jwt>} header and is validated against
 * the issuer configured via Spring Boot's {@code spring.security.oauth2.resourceserver.jwt.*}
 * properties.
 *
 * <p>Claims-to-authorities mapping is performed by the {@link
 * com.openelements.spring.base.security.SecurityConfig#jwtAuthenticationConverter()} bean:
 *
 * <ul>
 *   <li>The default {@link
 *       org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter}
 *       extracts {@code SCOPE_*} authorities from the {@code scope}/{@code scp} claim.
 *   <li>In addition, every entry in the JWT's {@code roles} claim is mapped to a {@code ROLE_<x>}
 *       authority so that Spring's {@code hasRole(...)} expressions work out of the box.
 * </ul>
 *
 * <h2>Helper services</h2>
 *
 * <p>Application code rarely interacts with Spring Security directly. Two services in this package
 * encapsulate the most common needs:
 *
 * <ul>
 *   <li>{@link com.openelements.spring.base.security.AuthService} — exposes the current {@link
 *       org.springframework.security.core.Authentication}, principal, and JWT, and converts the JWT
 *       claims into a typed {@link com.openelements.spring.base.security.UserInformation} record.
 *       For non-JWT principals (typically API-key authenticated requests) {@link
 *       com.openelements.spring.base.security.AuthService#getUserInformation()} returns the static
 *       {@code UserInformation("UNKNOWN", "UNKNOWN", null, null)} instead of throwing.
 *   <li>{@link com.openelements.spring.base.services.user.UserService} — looks up (or lazily
 *       creates) the {@link com.openelements.spring.base.services.user.UserEntity} that mirrors the
 *       JWT subject in the local database, and manages the user's avatar image.
 * </ul>
 *
 * <h2>Mechanism summary</h2>
 *
 * <table>
 *   <caption>Mechanism summary</caption>
 *   <tr><th>Concern</th><th>API key (external chain)</th><th>JWT (default chain)</th></tr>
 *   <tr><td>Path scope</td><td>{@code /api/external/**}</td>
 *       <td>everything else</td></tr>
 *   <tr><td>Header</td><td>{@code X-API-Key: crm_...}</td>
 *       <td>{@code Authorization: Bearer ...}</td></tr>
 *   <tr><td>Allowed methods</td><td>{@code GET}, {@code HEAD}, {@code OPTIONS}</td>
 *       <td>All HTTP methods</td></tr>
 *   <tr><td>Identity</td><td>{@code ApiKeyEntity} (principal)</td>
 *       <td>{@link org.springframework.security.oauth2.jwt.Jwt} (principal)</td></tr>
 *   <tr><td>Authorities</td><td>{@code ROLE_API_KEY}</td>
 *       <td>{@code SCOPE_*} + {@code ROLE_*} from JWT claims</td></tr>
 *   <tr><td>Storage</td><td>SHA-256 hash + display prefix</td>
 *       <td>Stateless — no server-side storage</td></tr>
 *   <tr><td>Validated by</td>
 *       <td>{@link
 *           com.openelements.spring.base.security.apikey.ApiKeyAuthenticationFilter}</td>
 *       <td>{@link
 *           org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter}</td></tr>
 * </table>
 */
package com.openelements.spring.base.security;
