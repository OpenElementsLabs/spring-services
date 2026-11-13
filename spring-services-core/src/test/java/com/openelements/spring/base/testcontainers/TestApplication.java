package com.openelements.spring.base.testcontainers;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test application context for integration tests with Testcontainers. Excludes OAuth2
 * auto-configuration since we mock authentication in tests.
 */
@SpringBootApplication(
    scanBasePackages = "com.openelements.spring.base",
    exclude = OAuth2ResourceServerAutoConfiguration.class)
@EntityScan(basePackages = "com.openelements.spring.base")
@EnableJpaRepositories(basePackages = "com.openelements.spring.base")
public class TestApplication {}
