package com.openelements.spring.base.security;

import com.openelements.spring.base.security.apikey.ApiKeyAuthenticationFilter;
import com.openelements.spring.base.services.apikey.ApiKeyConfig;
import com.openelements.spring.base.services.apikey.ApiKeyDataService;
import com.openelements.spring.base.services.user.UserConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
 * com.openelements.spring.base.security package documentation}. This configuration contributes two
 * strictly isolated {@link SecurityFilterChain} beans plus a {@link JwtAuthenticationConverter}:
 *
 * <ul>
 *   <li>{@link #externalApiFilterChain(HttpSecurity, ApiKeyAuthenticationFilter)}
 *       ({@code @Order(1)}) — matches {@code /api/external/**} and uses API-key authentication
 *       only. Read-only access (GET/HEAD/OPTIONS) is enforced declaratively at the chain level; all
 *       other HTTP methods are denied.
 *   <li>{@link #defaultFilterChain(HttpSecurity)} ({@code @Order(2)}) — matches everything else and
 *       uses OAuth2/JWT authentication only. The {@code X-API-Key} header is silently ignored on
 *       this chain.
 *   <li>{@link #jwtAuthenticationConverter()} — extends the default scope-to-authority mapping so
 *       that values from the JWT's {@code roles} claim are exposed as {@code ROLE_<x>} authorities,
 *       in addition to the {@code SCOPE_<x>} authorities derived from the standard {@code
 *       scope}/{@code scp} claim.
 * </ul>
 *
 * <p>Health-check ({@code /api/health/**}) and OpenAPI/Swagger endpoints remain anonymously
 * accessible on the default chain. CSRF protection is disabled on both chains because the platform
 * is a stateless REST API.
 */
@EnableWebSecurity
@AutoConfiguration
@EnableAutoConfiguration
@Import({ApiKeyConfig.class, UserConfig.class})
@ComponentScan
@EnableMethodSecurity
public class SecurityConfig {

  private final ApiKeyDataService apiKeyDataService;

  /**
   * @param apiKeyDataService the data service used to validate API keys; injected into the {@link
   *     ApiKeyAuthenticationFilter} that is registered on the external API chain
   */
  public SecurityConfig(final ApiKeyDataService apiKeyDataService) {
    this.apiKeyDataService = apiKeyDataService;
  }

  /**
   * Constructs the {@link ApiKeyAuthenticationFilter}.
   *
   * <p>Defined as a bean here (rather than annotating the filter class with {@code @Component}) so
   * that Spring Boot does not auto-register it as a servlet-level filter on every chain. The filter
   * must only run on the external API chain.
   *
   * @return the filter instance used by {@link #externalApiFilterChain(HttpSecurity,
   *     ApiKeyAuthenticationFilter)}
   */
  @Bean
  public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter() {
    return new ApiKeyAuthenticationFilter(apiKeyDataService);
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
   * Defines the external API filter chain.
   *
   * <p>Scoped to {@code /api/external/**}, this chain accepts only API-key authentication and only
   * read-only HTTP methods (GET, HEAD, OPTIONS). All other methods are rejected by the chain's
   * authorization rules with {@code 403 Forbidden}. JWT bearer tokens are not evaluated on this
   * chain — there is no OAuth2 resource server configured here.
   *
   * @param http the HTTP security builder
   * @param apiKeyAuthenticationFilter the filter that extracts and validates the {@code X-API-Key}
   *     header
   * @return the configured external API filter chain
   * @throws Exception if Spring Security fails to build the chain
   */
  @Bean
  @Order(1)
  public SecurityFilterChain externalApiFilterChain(
      final HttpSecurity http, final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter)
      throws Exception {
    http.securityMatcher("/api/external/**")
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/external/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.HEAD, "/api/external/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.OPTIONS, "/api/external/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(apiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class)
        .csrf(csrf -> csrf.disable());
    return http.build();
  }

  /**
   * Defines the default filter chain.
   *
   * <p>Matches every request not handled by {@link #externalApiFilterChain(HttpSecurity,
   * ApiKeyAuthenticationFilter)}. Uses OAuth2/JWT authentication exclusively; the {@code X-API-Key}
   * header is not evaluated on this chain. Health-check and Swagger UI/OpenAPI documentation
   * endpoints remain anonymously accessible.
   *
   * @param http the HTTP security builder
   * @return the configured default filter chain
   * @throws Exception if Spring Security fails to build the chain
   */
  @Bean
  @Order(2)
  public SecurityFilterChain defaultFilterChain(final HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/health/**")
                    .permitAll()
                    .requestMatchers(
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .csrf(csrf -> csrf.disable());
    return http.build();
  }
}
