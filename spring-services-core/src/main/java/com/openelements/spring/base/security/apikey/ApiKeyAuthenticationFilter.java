package com.openelements.spring.base.security.apikey;

import com.openelements.spring.base.security.roles.Roles;
import com.openelements.spring.base.services.apikey.ApiKeyDataService;
import com.openelements.spring.base.services.apikey.ApiKeyEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that authenticates requests carrying an {@code X-API-Key} header.
 *
 * <p>Registered exclusively on the external API filter chain by {@link
 * com.openelements.spring.base.security.SecurityConfig}, ahead of Spring Security's standard {@link
 * org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter}
 * anchor. This filter is <b>not</b> annotated with {@code @Component} — it must not be
 * auto-registered as a servlet-level filter, because that would cause it to run on the JWT chain
 * too and break the strict isolation between the two chains.
 *
 * <h2>Behaviour</h2>
 *
 * <ol>
 *   <li>If the {@code X-API-Key} header is <b>absent</b>, the filter does nothing and forwards the
 *       request — chain-level authorization rules will reject the request if no other
 *       authentication is supplied.
 *   <li>If the header is <b>present</b>, the raw key is hashed (SHA-256) and looked up via {@link
 *       ApiKeyDataService#authenticate(String)}.
 *       <ul>
 *         <li>Unknown key → respond {@code 401 Unauthorized} with a JSON error body and abort.
 *         <li>Known key → place an {@link ApiKeyAuthentication} token (with the {@code
 *             ROLE_API_KEY} authority) into the security context and continue.
 *       </ul>
 * </ol>
 *
 * <p>HTTP method enforcement (read-only access for API keys) is the responsibility of the chain's
 * {@code authorizeHttpRequests} configuration, not this filter.
 *
 * <p>This filter never reads or persists the raw key beyond the lifetime of the request — only the
 * hash is compared against the database.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final String API_KEY_HEADER = "X-API-Key";

  private final ApiKeyDataService apiKeyService;

  private final AuthenticationEntryPoint authenticationEntryPoint;

  /**
   * Holder strategy used to publish the authenticated {@link ApiKeyAuthentication}. Resolved once
   * at construction; honours any custom {@link SecurityContextHolder#setStrategyName(String)}
   * installed by the consuming application.
   */
  private final SecurityContextHolderStrategy securityContextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();

  /**
   * Creates a new filter that authenticates requests using their API key.
   *
   * @param apiKeyService the data service used to validate API keys
   * @param authenticationEntryPoint the entry point invoked on authentication failure — produces
   *     the uniform JSON {@code 401} response and the {@code WWW-Authenticate} header
   */
  public ApiKeyAuthenticationFilter(
      final ApiKeyDataService apiKeyService,
      final AuthenticationEntryPoint authenticationEntryPoint) {
    this.apiKeyService = Objects.requireNonNull(apiKeyService, "apiKeyService must not be null");
    this.authenticationEntryPoint =
        Objects.requireNonNull(
            authenticationEntryPoint, "authenticationEntryPoint must not be null");
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final String apiKey = request.getHeader(API_KEY_HEADER);

    if (apiKey == null) {
      filterChain.doFilter(request, response);
      return;
    }

    final var entity = apiKeyService.authenticate(apiKey);

    if (entity.isEmpty()) {
      authenticationEntryPoint.commence(
          request, response, new BadCredentialsException("Invalid API key"));
      return;
    }

    final ApiKeyAuthentication authentication = new ApiKeyAuthentication(entity.get());
    final SecurityContext context = securityContextHolderStrategy.createEmptyContext();
    context.setAuthentication(authentication);
    securityContextHolderStrategy.setContext(context);
    filterChain.doFilter(request, response);
  }

  /**
   * Spring Security {@link AbstractAuthenticationToken} representing a request that has been
   * authenticated via a valid API key.
   *
   * <p>The principal is the {@link ApiKeyEntity} that matched the key; the token's only granted
   * authority is {@code ROLE_API_KEY}, which downstream authorization rules can use to distinguish
   * API-key sessions from JWT sessions.
   */
  static class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final ApiKeyEntity apiKey;

    ApiKeyAuthentication(final ApiKeyEntity apiKey) {
      super(List.of(new SimpleGrantedAuthority(Roles.AUTHORITY_PREFIX + Roles.ROLE_API_KEY)));
      this.apiKey = apiKey;
      setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
      return null;
    }

    @Override
    public Object getPrincipal() {
      return apiKey;
    }
  }
}
