package com.openelements.spring.base.tenant;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the optional multi-tenancy feature.
 *
 * <p>Registered through this module's
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so the
 * feature self-activates whenever {@code spring-services-tenant} is on the classpath. The module
 * ships no optional third-party library, so — unlike the slack/mcp/email auto-configurations — there
 * is no {@code @ConditionalOnClass} guard: present on the classpath means active.
 *
 * <p>Co-locating in the {@code tenant} package is safe because {@link TenantConfig} registers
 * {@link TenantService} through an explicit {@code @Bean} instead of a {@code @ComponentScan} that
 * would otherwise sweep up this class. {@code @Import(TenantConfig.class)} / {@link EnableTenant}
 * remain valid for explicit wiring.
 */
@AutoConfiguration
@Import(TenantConfig.class)
public class TenantAutoConfiguration {

  /** Creates the tenant auto-configuration; wiring is delegated to {@link TenantConfig}. */
  public TenantAutoConfiguration() {}
}
