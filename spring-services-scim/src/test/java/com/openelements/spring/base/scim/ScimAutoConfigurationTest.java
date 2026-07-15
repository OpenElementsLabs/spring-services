package com.openelements.spring.base.scim;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.scim.ScimUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Activation-guard test for {@link ScimAutoConfiguration}: without {@code openelements.scim.token}
 * the feature must not register any bean, leaving {@code /scim/v2/**} unmapped (handled by the
 * default JWT chain).
 */
@DisplayName("ScimAutoConfiguration — activation guard")
class ScimAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ScimAutoConfiguration.class));

  @Test
  @DisplayName("Stays inert (no SCIM beans) when openelements.scim.token is not set")
  void inertWithoutToken() {
    contextRunner.run(
        context ->
            assertThat(context).hasNotFailed().doesNotHaveBean(ScimUserService.class));
  }
}
