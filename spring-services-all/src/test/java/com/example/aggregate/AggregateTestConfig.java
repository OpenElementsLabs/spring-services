package com.example.aggregate;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Test infrastructure for the aggregate context: a PostgreSQL Testcontainer and a stub
 * {@link JwtDecoder} required by the core JWT security chain (the aggregate test does not exercise
 * real JWT validation).
 */
@TestConfiguration(proxyBeanMethods = false)
public class AggregateTestConfig {

  @Bean
  @ServiceConnection
  public PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>("postgres:16-alpine");
  }

  @Bean
  public JwtDecoder testJwtDecoder() {
    return token -> {
      throw new UnsupportedOperationException("JWT validation is disabled in the aggregate test");
    };
  }
}
