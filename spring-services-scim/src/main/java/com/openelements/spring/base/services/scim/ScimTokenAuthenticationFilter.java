package com.openelements.spring.base.services.scim;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates {@code /scim/v2/**} requests against the single static shared-secret token
 * configured in {@link ScimProperties#getToken()}.
 *
 * <p>Extracts the {@code Authorization: Bearer <token>} header and compares it to the configured
 * token with a <strong>constant-time</strong> comparison ({@link MessageDigest#isEqual} over UTF-8
 * bytes) to avoid leaking the secret through a timing side channel. On a match it authenticates the
 * request as the reserved {@link ScimServicePrincipal SCIM service principal}; on a missing or wrong
 * token it leaves the context unauthenticated, so the chain's {@code authenticated()} rule triggers
 * the SCIM {@code 401} entry point.
 */
@Component
public class ScimTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final ScimProperties properties;

  private final SecurityContextHolderStrategy securityContextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();

  /**
   * Creates the filter.
   *
   * @param properties the SCIM properties holding the configured static token
   */
  public ScimTokenAuthenticationFilter(final ScimProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      final String presented = header.substring(BEARER_PREFIX.length());
      if (isConfiguredToken(presented)) {
        final UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                ScimServicePrincipal.USER_NAME, null, List.of());
        final SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
      }
    }
    filterChain.doFilter(request, response);
  }

  private boolean isConfiguredToken(final String presented) {
    final String configured = properties.getToken();
    if (configured == null || configured.isEmpty()) {
      return false;
    }
    return MessageDigest.isEqual(
        configured.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
  }
}
