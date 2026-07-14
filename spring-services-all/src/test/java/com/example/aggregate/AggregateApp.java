package com.example.aggregate;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;

/**
 * A zero-config consumer application with the <em>full</em> reactor on the classpath
 * ({@code spring-services-all}). It declares its own entity/repository ({@link Order}) and
 * <strong>no</strong> {@code @Import}, {@code @EntityScan}, or {@code @EnableJpaRepositories} — every
 * library module self-activates through its own auto-configuration.
 */
@SpringBootApplication(exclude = OAuth2ResourceServerAutoConfiguration.class)
public class AggregateApp {}
