/**
 * Authentication and authorization layer of the {@code spring-services} platform.
 *
 * <p>This package wires Spring Security so that every HTTP endpoint exposed by an application built
 * on the platform is protected by default and can be accessed through one of two authentication
 * mechanisms.
 *
 * <h2>Threat model and defaults</h2>
 *
 * The {@link com.openelements.spring.base.security.SecurityConfig#filterChain(
 * org.springframework.security.config.annotation.web.builders.HttpSecurity) security filter chain}
 * applies the following defaults:
 *
 * <ul>
 *   <li><b>Authenticated by default.</b> Every request must carry valid credentials. Only two
 *       categories of endpoints are permitted anonymously:
 *       <ul>
 *         <li>{@code /api/health/**} — for liveness/readiness probes.
 *         <li>{@code /swagger-ui.html}, {@code /swagger-ui/**} and {@code /v3/api-docs/**} — for
 *             the OpenAPI documentation UI.
 *       </ul>
 *   <li><b>CSRF disabled.</b> The platform exposes a stateless REST API consumed by external
 *       clients (browsers using bearer tokens, scripts using API keys, server-to-server calls).
 *       CSRF protection only adds value for cookie/session-based authentication, which is not used
 *       here.
 *   <li><b>No HTTP session.</b> Authentication is re-evaluated on every request from the
 *       credentials carried in headers.
 * </ul>
 *
 * <h2>Authentication mechanisms</h2>
 *
 * Two mechanisms are configured. They are mutually exclusive on a per-request basis and run in a
 * fixed order so behaviour is deterministic.
 *
 * <h3>1. {@code X-API-Key} header (read-only access)</h3>
 *
 * <p>The {@link com.openelements.spring.base.security.apikey.ApiKeyAuthenticationFilter} runs
 * <em>before</em> the JWT filter ({@link
 * org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter})
 * and inspects the {@code X-API-Key} request header.
 *
 * <ul>
 *   <li>If the header is <b>absent</b>, the filter passes the request through to the JWT chain
 *       without changes — JWT-based authentication is the default.
 *   <li>If the header is <b>present</b>, the raw key is hashed with SHA-256 and looked up in the
 *       database via {@link
 *       com.openelements.spring.base.services.apikey.ApiKeyDataService#authenticate(String)}. Only
 *       the hash is stored on disk, never the raw key.
 *       <ul>
 *         <li>Unknown key → {@code 401 Unauthorized} (with a JSON error body) and the request is
 *             aborted.
 *         <li>Known key but the request method is not {@code GET}, {@code HEAD} or {@code OPTIONS}
 *             → {@code 403 Forbidden}. <b>API keys grant read-only access</b>; mutating operations
 *             must use a JWT.
 *         <li>Known key on a read-only method → an {@code ApiKeyAuthentication} token with the
 *             {@code ROLE_API_KEY} authority is placed into the {@link
 *             org.springframework.security.core.context.SecurityContextHolder}, and the request
 *             continues.
 *       </ul>
 * </ul>
 *
 * Keys are issued through {@link
 * com.openelements.spring.base.services.apikey.ApiKeyDataService#create(
 * com.openelements.spring.base.services.apikey.ApiKeyCreateDto)}: a cryptographically strong random
 * value (prefix {@code crm_} plus 48 random alphanumeric characters generated via {@link
 * java.security.SecureRandom}) is returned to the caller exactly once. Only its SHA-256 hash and a
 * short display prefix (e.g. {@code crm_AbCd...wXyZ}) are persisted, so a leaked database snapshot
 * does not expose usable credentials.
 *
 * <h3>2. OAuth2 / JWT bearer token (full access)</h3>
 *
 * <p>When no API key is present the request flows into Spring Security's standard OAuth2 resource
 * server filter. The token must be supplied in the {@code Authorization: Bearer <jwt>} header and
 * is validated against the issuer configured via Spring Boot's {@code
 * spring.security.oauth2.resourceserver.jwt.*} properties.
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
 * Application code rarely interacts with Spring Security directly. Two services in this package
 * encapsulate the most common needs:
 *
 * <ul>
 *   <li>{@link com.openelements.spring.base.security.AuthService} — exposes the current {@link
 *       org.springframework.security.core.Authentication}, principal, and JWT, and converts the JWT
 *       claims into a typed {@link com.openelements.spring.base.security.UserInformation} record.
 *   <li>{@link com.openelements.spring.base.security.user.UserService} — looks up (or lazily
 *       creates) the {@link com.openelements.spring.base.security.user.UserEntity} that mirrors the
 *       JWT subject in the local database, and manages the user's avatar image.
 * </ul>
 *
 * <h2>Mechanism summary</h2>
 *
 * <table>
 *   <caption>Mechanism summary</caption>
 *   <tr><th>Concern</th><th>API key</th><th>JWT</th></tr>
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
