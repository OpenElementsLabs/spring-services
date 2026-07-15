package com.openelements.spring.base.services.scim;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Registers the isolated SCIM security filter chain.
 *
 * <p>Following the spec-006 discipline of strictly separated chains, this adds a third
 * {@link SecurityFilterChain} that matches {@code /scim/v2/**} and authenticates it with the static
 * SCIM token ({@link ScimTokenAuthenticationFilter}) — never JWT or API-key. It is ordered
 * <em>ahead</em> of the default JWT chain (which has no matcher and is the fallback), so SCIM
 * requests are claimed here and never reach the JWT resource-server chain. A leaked SCIM token
 * therefore cannot be used against {@code /api/**} and vice versa.
 */
@Configuration(proxyBeanMethods = false)
public class ScimSecurityConfig {

  /** Creates the SCIM security configuration. */
  public ScimSecurityConfig() {}

  /**
   * Builds the {@code /scim/v2/**} security filter chain.
   *
   * @param http the HTTP security builder
   * @param scimTokenFilter the static-token authentication filter
   * @param scimAuthenticationEntryPoint the SCIM-shaped {@code 401} entry point
   * @return the configured SCIM filter chain
   * @throws Exception if Spring Security fails to build the chain
   */
  @Bean
  @Order(0)
  public SecurityFilterChain scimFilterChain(
      final HttpSecurity http,
      final ScimTokenAuthenticationFilter scimTokenFilter,
      final ScimAuthenticationEntryPoint scimAuthenticationEntryPoint)
      throws Exception {
    http.securityMatcher("/scim/v2/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .addFilterBefore(scimTokenFilter, BearerTokenAuthenticationFilter.class)
        .exceptionHandling(e -> e.authenticationEntryPoint(scimAuthenticationEntryPoint))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable());
    return http.build();
  }
}
