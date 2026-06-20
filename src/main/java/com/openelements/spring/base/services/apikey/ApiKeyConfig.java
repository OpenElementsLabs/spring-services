package com.openelements.spring.base.services.apikey;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the API key feature.
 *
 * <p>Component-scans this package so that {@link ApiKeyDataService} and {@link ApiKeyRepository}
 * are picked up. Imported by {@link com.openelements.spring.base.security.SecurityConfig}, which
 * wires them into the security filter chain via {@link
 * com.openelements.spring.base.security.apikey.ApiKeyAuthenticationFilter}.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = ApiKeyConfig.class)
public class ApiKeyConfig {}
