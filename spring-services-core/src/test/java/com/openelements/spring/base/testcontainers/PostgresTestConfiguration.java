package com.openelements.spring.base.testcontainers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers configuration that starts a PostgreSQL container and provides test beans
 * needed for the application context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

  @Bean
  @ServiceConnection
  public PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>("postgres:16-alpine")
        // Pre-create the dedicated schema so Hibernate's create-drop startup DROP phase does not
        // log a CommandAcceptanceException for the not-yet-existing oe_spring_services schema.
        .withInitScript("db/create-schema.sql");
  }

  @Bean
  public RestClient webhookRestClient() {
    return RestClient.builder().build();
  }

  /**
   * Stub {@link JwtDecoder} required by the library's default
   * {@link org.springframework.security.web.SecurityFilterChain}, which wires
   * {@code oauth2ResourceServer.jwt(...)}. The integration tests do not exercise actual JWT
   * validation — they mock {@link com.openelements.spring.base.security.AuthService} — but the
   * bean must exist for the chain factory method to instantiate.
   *
   * <p>Note: a custom {@code testSecurityFilterChain} matching {@code anyRequest()} would
   * conflict with the library's {@code defaultFilterChain} (Spring Security 6.2+ rejects two
   * unscoped chains). The integration tests invoke services directly via {@code @Autowired}
   * beans rather than through HTTP, so the production filter chains can run as-is without
   * affecting test behaviour.
   */
  @Bean
  public JwtDecoder testJwtDecoder() {
    return token -> {
      throw new UnsupportedOperationException("JWT validation is disabled in integration tests");
    };
  }
}
