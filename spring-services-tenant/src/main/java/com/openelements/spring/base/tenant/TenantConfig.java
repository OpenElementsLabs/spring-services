package com.openelements.spring.base.tenant;

import com.openelements.spring.base.security.AuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the multi-tenancy feature.
 *
 * <p>Registers {@link TenantService} as an explicit {@code @Bean} (rather than component-scanning
 * the package) so that {@link TenantAutoConfiguration}, which lives in the same package, is not
 * swept up and registered twice. Can be imported directly, via {@link EnableTenant}, or by the
 * module's auto-configuration.
 */
@Configuration(proxyBeanMethods = false)
public class TenantConfig {

  /** Creates the configuration. */
  public TenantConfig() {}

  /**
   * Registers the tenant service.
   *
   * @param authService the service used to resolve the authenticated principal
   * @return the tenant service
   */
  @Bean
  public TenantService tenantService(final AuthService authService) {
    return new TenantService(authService);
  }
}
