package com.openelements.spring.base.scim;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;

/**
 * Minimal boot application for the SCIM integration tests. Relies entirely on auto-configuration:
 * the core starter registers the library packages/entities and the SCIM starter activates because
 * {@code openelements.scim.token} is set in the test profile. OAuth2 resource-server auto-config is
 * excluded (a stub {@code JwtDecoder} is supplied by the test configuration for the core JWT chain).
 */
@SpringBootApplication(exclude = OAuth2ResourceServerAutoConfiguration.class)
public class ScimTestApp {}
