package com.openelements.spring.base.services.apikey;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring auto-configuration for the API key feature.
 *
 * <p>Component-scans this package so that the {@link ApiKeyDataService} and {@link
 * ApiKeyRepository} beans are picked up. Imported by {@link
 * com.openelements.spring.base.security.SecurityConfig}, which wires them into the security filter
 * chain via {@link com.openelements.spring.base.security.apikey.ApiKeyAuthenticationFilter}.
 */
@Configuration
@ComponentScan
@AutoConfiguration
@EnableAutoConfiguration
public class ApiKeyConfig {}
