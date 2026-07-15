package com.openelements.spring.base.scim;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.PostgreSQLContainer;

/** Test infrastructure: a PostgreSQL Testcontainer and a stub {@link JwtDecoder} for the JWT chain. */
@TestConfiguration(proxyBeanMethods = false)
public class ScimTestConfiguration {

  /**
   * Provides the PostgreSQL Testcontainer wired to Spring Boot via {@code @ServiceConnection}.
   *
   * @return the PostgreSQL container
   */
  @Bean
  @ServiceConnection
  public PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>("postgres:16-alpine");
  }

  /**
   * Stub {@link JwtDecoder} required by the core JWT security chain; SCIM tests never exercise JWT
   * validation.
   *
   * @return a decoder that rejects any token as invalid (so the JWT chain answers {@code 401},
   *     matching how a real decoder treats a non-JWT bearer token)
   */
  @Bean
  public JwtDecoder testJwtDecoder() {
    return token -> {
      throw new BadJwtException("JWT validation is disabled in SCIM tests");
    };
  }
}
