package com.openelements.spring.base.tenant;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring auto-configuration for the multi-tenancy feature.
 *
 * <p>Component-scans this package so that {@link TenantService} and the tenant-aware data
 * abstractions are registered. Can be imported either directly, via {@link EnableTenant}, or as
 * part of {@link com.openelements.spring.base.FullSpringServiceConfig}.
 */
@Configuration
@ComponentScan
@AutoConfiguration
@EnableAutoConfiguration
public class TenantConfig {}
