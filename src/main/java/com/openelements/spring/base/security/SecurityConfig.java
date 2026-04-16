package com.openelements.spring.base.security;

import com.openelements.spring.base.security.apikey.ApiKeyAuthenticationFilter;
import com.openelements.spring.base.services.apikey.ApiKeyConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Auto-configuration that wires Spring Security for the {@code spring-services} platform.
 *
 * <p>For a high-level description of the security model see the {@linkplain
 * com.openelements.spring.base.security package documentation}. The two key beans contributed by
 * this configuration are:
 *
 * <ul>
 *   <li>{@link #filterChain(HttpSecurity)} — defines which endpoints are public, enables OAuth2
 *       resource-server JWT validation, and inserts the {@link ApiKeyAuthenticationFilter}
 *       <em>before</em> the standard {@link BearerTokenAuthenticationFilter} so that an {@code
 *       X-API-Key} header is evaluated first.
 *   <li>{@link #jwtAuthenticationConverter()} — extends the default scope-to-authority mapping so
 *       that values from the JWT's {@code roles} claim are exposed as {@code ROLE_<x>} authorities,
 *       in addition to the {@code SCOPE_<x>} authorities derived from the standard {@code
 *       scope}/{@code scp} claim.
 * </ul>
 *
 * <p>Anonymous access is granted only to health-check endpoints ({@code /api/health/**}) and to the
 * Swagger UI / OpenAPI documentation ({@code /swagger-ui.html}, {@code /swagger-ui/**}, {@code
 * /v3/api-docs/**}). All other requests must be authenticated. CSRF protection is disabled because
 * the platform is a stateless REST API.
 */
@EnableWebSecurity
@AutoConfiguration
@EnableAutoConfiguration
@Import(ApiKeyConfig.class)
@ComponentScan
public class SecurityConfig {

  private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

  /**
   * @param apiKeyAuthenticationFilter the filter that handles {@code X-API-Key} authentication and
   *     is registered ahead of the JWT bearer token filter
   */
  public SecurityConfig(final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
    this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
  }

  /**
   * Builds the converter that turns a validated JWT into a Spring Security {@link
   * org.springframework.security.core.Authentication}.
   *
   * <p>In addition to the standard {@code SCOPE_*} authorities derived from the {@code scope}/
   * {@code scp} claim by {@link JwtGrantedAuthoritiesConverter}, every entry in the {@code roles}
   * claim is mapped to a {@code ROLE_<x>} authority, which makes Spring's {@code hasRole(...)}
   * expressions work without further configuration.
   *
   * @return the JWT authentication converter
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    final JwtAuthenticationConverter authConverter = new JwtAuthenticationConverter();
    authConverter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          final Collection<GrantedAuthority> authorities =
              new ArrayList<>(scopeConverter.convert(jwt));
          final List<String> roles = jwt.getClaimAsStringList("roles");
          if (roles != null) {
            roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);
          }
          return authorities;
        });
    return authConverter;
  }

  /**
   * Defines the security filter chain.
   *
   * <p>Order of relevant filters and rules:
   *
   * <ol>
   *   <li>Authorization rules: {@code /api/health/**} and the Swagger UI endpoints are public; all
   *       other requests must be authenticated.
   *   <li>OAuth2 resource server (JWT) is enabled with the converter from {@link
   *       #jwtAuthenticationConverter()}.
   *   <li>{@link ApiKeyAuthenticationFilter} is inserted <em>before</em> {@link
   *       BearerTokenAuthenticationFilter}, so the {@code X-API-Key} header is checked first.
   *   <li>CSRF protection is disabled (stateless REST API).
   * </ol>
   *
   * @param http the HTTP security builder
   * @return the configured filter chain
   * @throws Exception if Spring Security fails to build the chain
   */
  @Bean
  public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/health/**")
                    .permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .addFilterBefore(apiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class)
        .csrf(csrf -> csrf.disable());
    return http.build();
  }
}
