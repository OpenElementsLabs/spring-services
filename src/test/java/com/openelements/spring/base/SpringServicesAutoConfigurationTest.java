package com.openelements.spring.base;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Activation-guard test for {@link SpringServicesAutoConfiguration}.
 *
 * <p>Verifies the {@code @ConditionalOnClass(EntityManagerFactory.class)} guard: in an application
 * without JPA on the classpath the starter must stay inert rather than fail trying to configure
 * persistence. Driven by an {@link ApplicationContextRunner} with a {@link FilteredClassLoader} that
 * hides {@link EntityManagerFactory}, so no database is involved.
 *
 * <p>The positive case (the auto-configuration activating and wiring the full platform when JPA is
 * present) is covered end-to-end by {@code com.example.app.StarterAutoConfigurationIntegrationTest}.
 */
@DisplayName("SpringServicesAutoConfiguration — activation guard")
class SpringServicesAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SpringServicesAutoConfiguration.class));

  @Test
  @DisplayName("Backs off (does not activate) when EntityManagerFactory is absent from the classpath")
  void backsOffWithoutJpa() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(EntityManagerFactory.class))
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(SpringServicesAutoConfiguration.class));
  }
}
