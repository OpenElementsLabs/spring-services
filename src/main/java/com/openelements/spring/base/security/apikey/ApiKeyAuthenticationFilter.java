package com.openelements.spring.base.security.apikey;

import com.openelements.spring.base.services.apikey.ApiKeyDataService;
import com.openelements.spring.base.services.apikey.ApiKeyEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that authenticates requests carrying an {@code X-API-Key} header.
 *
 * <p>Registered ahead of Spring Security's standard {@link
 * org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter}
 * by {@link com.openelements.spring.base.security.SecurityConfig}, so API-key authentication is
 * attempted first and JWT authentication only runs as a fallback.
 *
 * <h2>Behaviour</h2>
 *
 * <ol>
 *   <li>If the {@code X-API-Key} header is <b>absent</b>, the filter does nothing and forwards the
 *       request — JWT authentication takes over.
 *   <li>If the header is <b>present</b>, the raw key is hashed (SHA-256) and looked up via {@link
 *       ApiKeyDataService#authenticate(String)}.
 *       <ul>
 *         <li>Unknown key → respond {@code 401 Unauthorized} with a JSON error body and abort.
 *         <li>Known key but the HTTP method is not one of {@code GET}, {@code HEAD}, {@code
 *             OPTIONS} → respond {@code 403 Forbidden}. <b>API keys grant read-only access
 *             only.</b>
 *         <li>Known key on a read-only method → place an {@link ApiKeyAuthentication} token (with
 *             the {@code ROLE_API_KEY} authority) into the security context and continue.
 *       </ul>
 * </ol>
 *
 * <p>This filter never reads or persists the raw key beyond the lifetime of the request — only the
 * hash is compared against the database.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final String API_KEY_HEADER = "X-API-Key";
  private static final Set<String> READ_ONLY_METHODS =
      Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

  private final ApiKeyDataService apiKeyService;

  /**
   * @param apiKeyService the data service used to validate API keys
   */
  public ApiKeyAuthenticationFilter(final ApiKeyDataService apiKeyService) {
    this.apiKeyService = Objects.requireNonNull(apiKeyService, "apiKeyService must not be null");
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final String apiKey = request.getHeader(API_KEY_HEADER);

    if (apiKey == null) {
      // No API key header — pass through to JWT auth
      filterChain.doFilter(request, response);
      return;
    }

    // API key header present — attempt authentication
    final var entity = apiKeyService.authenticate(apiKey);

    if (entity.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"error\":\"Invalid API key\"}");
      return;
    }

    // Key is valid — check if method is read-only
    if (!READ_ONLY_METHODS.contains(request.getMethod())) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"error\":\"API keys only grant read-only access\"}");
      return;
    }

    // Authenticate the request
    final ApiKeyAuthentication authentication = new ApiKeyAuthentication(entity.get());
    SecurityContextHolder.getContext().setAuthentication(authentication);
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
      super(List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
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
