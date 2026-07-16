package com.openelements.spring.base.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.security.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies that {@link TenantAutoConfiguration} self-activates and registers exactly one
 * {@link TenantService} bean — i.e. the switch from {@code @ComponentScan} to an explicit
 * {@code @Bean} in {@link TenantConfig} does not double-register the service.
 */
@DisplayName("TenantAutoConfiguration — self-activation")
class TenantAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(AuthService.class, AuthService::new)
          .withConfiguration(AutoConfigurations.of(TenantAutoConfiguration.class));

  @Test
  @DisplayName("Exactly one TenantService bean is registered on the classpath")
  void singleTenantServiceBean() {
    contextRunner.run(
        context -> assertThat(context).hasNotFailed().hasSingleBean(TenantService.class));
  }
}
