package com.openelements.spring.base.tenant;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the multi-tenancy feature.
 *
 * <p>Component-scans this package so that {@link TenantService} and the tenant-aware data
 * abstractions are registered. Can be imported either directly, via {@link EnableTenant}, or as
 * part of {@link com.openelements.spring.base.FullSpringServiceConfig}.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = TenantConfig.class)
public class TenantConfig {

  /** Creates the configuration; the tenant beans are registered via component scanning. */
  public TenantConfig() {}
}
