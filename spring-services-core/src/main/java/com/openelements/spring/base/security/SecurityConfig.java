package com.openelements.spring.base.security;

import com.openelements.spring.base.problem.ProblemAccessDeniedHandler;
import com.openelements.spring.base.problem.ProblemAuthenticationEntryPoint;
import com.openelements.spring.base.problem.ProblemFactory;
import com.openelements.spring.base.problem.ProblemResponseWriter;
import com.openelements.spring.base.security.apikey.ApiKeyAuthenticationFilter;
import com.openelements.spring.base.security.roles.Roles;
import com.openelements.spring.base.services.apikey.ApiKeyConfig;
import com.openelements.spring.base.services.apikey.ApiKeyDataService;
import com.openelements.spring.base.services.user.UserConfig;
import jakarta.servlet.DispatcherType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Configuration that wires Spring Security for the {@code spring-services} platform.
 *
 * <p>For a high-level description of the security model see the {@linkplain
 * com.openelements.spring.base.security package documentation}. This configuration contributes two
 * strictly isolated {@link SecurityFilterChain} beans plus a {@link JwtAuthenticationConverter}:
 *
 * <ul>
 *   <li>{@link #externalApiFilterChain(HttpSecurity, ApiKeyAuthenticationFilter,
 *       AuthenticationEntryPoint, AccessDeniedHandler)} ({@code @Order(1)}) — matches
 *       {@code /api/external/**} and
 *       uses API-key authentication only. Read-only access (GET/HEAD/OPTIONS) is enforced
 *       declaratively at the chain level; all other HTTP methods are denied.
 *   <li>{@link #defaultFilterChain(HttpSecurity, AuthenticationEntryPoint, AccessDeniedHandler)}
 *       ({@code @Order(2)}) — matches everything else and uses OAuth2/JWT authentication only. The
 *       {@code X-API-Key} header is silently ignored on this chain.
 *   <li>{@link #jwtAuthenticationConverter()} — extends the default scope-to-authority mapping so
 *       that values from the JWT's {@code roles} claim are exposed as {@code ROLE_<x>} authorities,
 *       in addition to the {@code SCOPE_<x>} authorities derived from the standard {@code
 *       scope}/{@code scp} claim.
 * </ul>
 *
 * <p>Health-check ({@code /api/health/**}) and OpenAPI/Swagger endpoints remain anonymously
 * accessible on the default chain. CSRF protection is disabled on both chains because the platform
 * is a stateless REST API.
 *
 * <h2>Error responses</h2>
 *
 * <p>While {@code openelements.problem-details.enabled} is on (the default), both chains answer with
 * RFC 9457 problems: {@code ProblemAuthenticationEntryPoint} for a {@code 401} and
 * {@code ProblemAccessDeniedHandler} for a {@code 403}. Setting the property to {@code false}
 * restores exactly what this library produced before — {@link JsonAuthenticationEntryPoint} on the
 * {@code 401}, Spring Security's empty body on the {@code 403} — so the switch is a complete
 * rollback rather than a partial one. The bean <em>names</em> stay the same in both cases, because
 * other modules inject them by qualifier.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = SecurityConfig.class)
@EnableWebSecurity
@EnableMethodSecurity
@Import({ApiKeyConfig.class, UserConfig.class})
public class SecurityConfig {

  private final ApiKeyDataService apiKeyDataService;

  /**
   * Creates the security configuration.
   *
   * @param apiKeyDataService the data service used to validate API keys; injected into the {@link
   *     ApiKeyAuthenticationFilter} that is registered on the external API chain
   */
  public SecurityConfig(final ApiKeyDataService apiKeyDataService) {
    this.apiKeyDataService = apiKeyDataService;
  }

  /** {@code WWW-Authenticate} value of the JWT chain, per RFC 7235 §3.1. */
  private static final String BEARER_SCHEME = "Bearer";

  /** {@code WWW-Authenticate} value of the external API-key chain, per RFC 7235 §3.1. */
  private static final String API_KEY_SCHEME = "ApiKey realm=\"external\"";

  /**
   * Entry point for the JWT chain — issues a {@code WWW-Authenticate: Bearer} header alongside an
   * RFC 9457 {@code 401} problem. Used by Spring Security's
   * {@code oauth2ResourceServer.authenticationEntryPoint(...)} hook on the default chain.
   *
   * @param problemFactory builds the problem body
   * @param responseWriter writes it onto the raw response
   * @return the entry point bean
   */
  @Bean(name = "jwtAuthenticationEntryPoint")
  @ConditionalOnProperty(
      prefix = "openelements.problem-details",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public AuthenticationEntryPoint problemJwtAuthenticationEntryPoint(
      final ProblemFactory problemFactory, final ProblemResponseWriter responseWriter) {
    return new ProblemAuthenticationEntryPoint(problemFactory, responseWriter, BEARER_SCHEME);
  }

  /**
   * Entry point for the external API-key chain — issues a {@code WWW-Authenticate: ApiKey} header
   * alongside an RFC 9457 {@code 401} problem. Invoked by {@link ApiKeyAuthenticationFilter} on an
   * invalid API key, and by Spring Security's
   * {@code exceptionHandling.authenticationEntryPoint(...)} hook on the external chain.
   *
   * @param problemFactory builds the problem body
   * @param responseWriter writes it onto the raw response
   * @return the entry point bean
   */
  @Bean(name = "apiKeyAuthenticationEntryPoint")
  @ConditionalOnProperty(
      prefix = "openelements.problem-details",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public AuthenticationEntryPoint problemApiKeyAuthenticationEntryPoint(
      final ProblemFactory problemFactory, final ProblemResponseWriter responseWriter) {
    return new ProblemAuthenticationEntryPoint(problemFactory, responseWriter, API_KEY_SCHEME);
  }

  /**
   * Handles every denial Spring Security decides on — a chain rule, a {@code @PreAuthorize}, or a
   * deactivated account — with an RFC 9457 {@code 403} problem instead of Spring's empty body.
   *
   * @param problemFactory builds the problem body
   * @param responseWriter writes it onto the raw response
   * @return the access-denied handler; the bean simply does not exist while problem details are off,
   *     in which case Spring Security's default empty-body behaviour returns
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "openelements.problem-details",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public AccessDeniedHandler problemAccessDeniedHandler(
      final ProblemFactory problemFactory, final ProblemResponseWriter responseWriter) {
    return new ProblemAccessDeniedHandler(problemFactory, responseWriter);
  }

  /**
   * Entry point for the JWT chain while {@code openelements.problem-details.enabled=false} — the
   * body this library produced before RFC 9457 problems existed.
   *
   * @param objectMapper the application's Jackson mapper
   * @return the entry point bean
   */
  @Bean(name = "jwtAuthenticationEntryPoint")
  @ConditionalOnProperty(
      prefix = "openelements.problem-details",
      name = "enabled",
      havingValue = "false")
  public AuthenticationEntryPoint jsonJwtAuthenticationEntryPoint(final ObjectMapper objectMapper) {
    return new JsonAuthenticationEntryPoint(objectMapper, BEARER_SCHEME);
  }

  /**
   * Entry point for the external API-key chain while
   * {@code openelements.problem-details.enabled=false}.
   *
   * @param objectMapper the application's Jackson mapper
   * @return the entry point bean
   */
  @Bean(name = "apiKeyAuthenticationEntryPoint")
  @ConditionalOnProperty(
      prefix = "openelements.problem-details",
      name = "enabled",
      havingValue = "false")
  public AuthenticationEntryPoint jsonApiKeyAuthenticationEntryPoint(
      final ObjectMapper objectMapper) {
    return new JsonAuthenticationEntryPoint(objectMapper, API_KEY_SCHEME);
  }

  /**
   * Constructs the {@link ApiKeyAuthenticationFilter}.
   *
   * <p>Defined as a bean here (rather than annotating the filter class with {@code @Component}) so
   * that Spring Boot does not auto-register it as a servlet-level filter on every chain. The filter
   * must only run on the external API chain.
   *
   * @param apiKeyAuthenticationEntryPoint the entry point that renders the {@code 401} on invalid
   *     API keys; same bean wired into the external chain's {@code exceptionHandling}
   * @return the filter instance used by {@link #externalApiFilterChain(HttpSecurity,
   *     ApiKeyAuthenticationFilter, AuthenticationEntryPoint, AccessDeniedHandler)}
   */
  @Bean
  public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(
      @Qualifier("apiKeyAuthenticationEntryPoint")
          final AuthenticationEntryPoint apiKeyAuthenticationEntryPoint) {
    return new ApiKeyAuthenticationFilter(apiKeyDataService, apiKeyAuthenticationEntryPoint);
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
                .map(role -> new SimpleGrantedAuthority(Roles.AUTHORITY_PREFIX + role))
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
   * @param apiKeyAuthenticationEntryPoint the entry point that renders authentication failures on
   *     this chain as a {@code 401} response
   * @param accessDeniedHandler renders the {@code 403} for the denied write methods; absent while
   *     {@code openelements.problem-details.enabled=false}, in which case Spring Security's default
   *     empty-body handler applies, as it did before this feature
   * @return the configured external API filter chain
   * @throws Exception if Spring Security fails to build the chain
   */
  @Bean
  @Order(1)
  public SecurityFilterChain externalApiFilterChain(
      final HttpSecurity http,
      final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
      @Qualifier("apiKeyAuthenticationEntryPoint")
          final AuthenticationEntryPoint apiKeyAuthenticationEntryPoint,
      final @Nullable AccessDeniedHandler accessDeniedHandler)
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
        .exceptionHandling(
            e -> {
              e.authenticationEntryPoint(apiKeyAuthenticationEntryPoint);
              if (accessDeniedHandler != null) {
                e.accessDeniedHandler(accessDeniedHandler);
              }
            })
        .csrf(csrf -> csrf.disable());
    return http.build();
  }

  /**
   * Defines the default filter chain.
   *
   * <p>Matches every request not handled by {@link #externalApiFilterChain(HttpSecurity,
   * ApiKeyAuthenticationFilter, AuthenticationEntryPoint, AccessDeniedHandler)}. Uses OAuth2/JWT
   * authentication exclusively; the {@code X-API-Key} header is not evaluated on this chain.
   * Health-check and OpenAPI/Swagger documentation endpoints remain anonymously accessible.
   *
   * <p>{@link DispatcherType#ERROR} dispatches are permitted while problem details are on. The
   * servlet container re-dispatches a {@code sendError(...)} — from a filter, or from the container
   * itself — through this chain, and {@code anyRequest().authenticated()} would otherwise turn an
   * anonymous caller's {@code 429} into a {@code 401}. Permitting the dispatch exposes nothing but
   * the rendering of {@code /error}; no handler of the application becomes reachable through it.
   *
   * @param http the HTTP security builder
   * @param jwtAuthenticationEntryPoint the entry point that produces the {@code 401} on the JWT
   *     chain
   * @param accessDeniedHandler renders the {@code 403} for method security and for a deactivated
   *     account; absent while {@code openelements.problem-details.enabled=false}, in which case
   *     Spring Security's default empty-body handler applies, as it did before this feature
   * @return the configured default filter chain
   * @throws Exception if Spring Security fails to build the chain
   */
  @Bean
  @Order(2)
  public SecurityFilterChain defaultFilterChain(
      final HttpSecurity http,
      @Qualifier("jwtAuthenticationEntryPoint")
          final AuthenticationEntryPoint jwtAuthenticationEntryPoint,
      final @Nullable AccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.authorizeHttpRequests(
            auth -> {
                if (accessDeniedHandler != null) {
                  auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                }
                auth.requestMatchers("/api/health/**")
                    .permitAll()
                    .requestMatchers(
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated();
            })
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint))
        // The entry point stays registered through oauth2ResourceServer, as before this feature;
        // only the access-denied handler is added here, so enabled=false leaves the chain untouched.
        .exceptionHandling(
            e -> {
              if (accessDeniedHandler != null) {
                e.accessDeniedHandler(accessDeniedHandler);
              }
            })
        .csrf(csrf -> csrf.disable());
    return http.build();
  }
}
