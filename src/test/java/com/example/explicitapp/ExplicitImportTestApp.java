package com.example.explicitapp;

import com.openelements.spring.base.FullSpringServiceConfig;
import com.openelements.spring.base.SpringServicesAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * A consumer application that wires the library the <em>old</em>, explicit way: the Spring Boot
 * starter ({@link SpringServicesAutoConfiguration}) is excluded, and the library is brought in via
 * {@code @Import(FullSpringServiceConfig.class)} plus explicit {@code @EntityScan} /
 * {@code @EnableJpaRepositories} over the library root. This proves the pre-starter integration
 * contract still works and produces no duplicate-bean errors.
 */
@SpringBootApplication(
    exclude = {
      OAuth2ResourceServerAutoConfiguration.class,
      SpringServicesAutoConfiguration.class
    })
@Import(FullSpringServiceConfig.class)
@EntityScan("com.openelements.spring.base")
@EnableJpaRepositories("com.openelements.spring.base")
public class ExplicitImportTestApp {}
