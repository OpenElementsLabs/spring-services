package com.openelements.spring.base.info;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.SpringServicesCoreAutoConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * Context-level tests for {@link ApplicationInfoAutoConfiguration}.
 *
 * <p>Covers the three properties that motivated a dedicated, JPA-independent auto-configuration:
 * the service is available with no {@link EntityManagerFactory} on the classpath (and the JPA-guarded
 * {@link SpringServicesCoreAutoConfiguration} stays inert), an application can substitute its own
 * service, and a malformed SBOM on the classpath does not prevent startup.
 *
 * <p>Driven by {@link ApplicationContextRunner}; the no-JPA case uses a {@link FilteredClassLoader}
 * so no database is involved.
 *
 * <p><b>Mock-Audit.</b> Zero mocks — real Spring contexts, a real classpath fixture.
 */
@DisplayName("ApplicationInfoAutoConfiguration")
class ApplicationInfoAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ApplicationInfoAutoConfiguration.class));

  @Test
  @DisplayName("The service is available even without JPA, and the JPA starter stays inert.")
  void availableWithoutJpa() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ApplicationInfoAutoConfiguration.class, SpringServicesCoreAutoConfiguration.class))
        .withClassLoader(new FilteredClassLoader(EntityManagerFactory.class))
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(ApplicationInfoService.class)
                    .doesNotHaveBean(SpringServicesCoreAutoConfiguration.class));
  }

  @Test
  @DisplayName("An application can substitute its own ApplicationInfoService bean.")
  void applicationCanSubstituteOwnService() {
    contextRunner
        .withUserConfiguration(CustomServiceConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ApplicationInfoService.class);
              assertThat(context.getBean(ApplicationInfoService.class))
                  .isInstanceOf(CustomApplicationInfoService.class);
            });
  }

  @Test
  @DisplayName("A malformed SBOM on the classpath does not prevent startup and logs a warning.")
  @ExtendWith(OutputCaptureExtension.class)
  void malformedSbomDoesNotPreventStartup(final CapturedOutput output) {
    contextRunner
        .withPropertyValues("openelements.info.sbom.location=classpath:sbom/malformed.cdx.json")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(ApplicationInfoService.class);
              assertThat(context.getBean(ApplicationInfoService.class).findSbom()).isEmpty();
              assertThat(output).contains("malformed.cdx.json");
            });
  }

  /** User configuration that declares a distinguishable {@link ApplicationInfoService} subclass. */
  @Configuration(proxyBeanMethods = false)
  static class CustomServiceConfig {

    @Bean
    ApplicationInfoService applicationInfoService(
        final ObjectProvider<BuildProperties> buildProperties,
        final ObjectProvider<GitProperties> gitProperties,
        final ResourceLoader resourceLoader) {
      return new CustomApplicationInfoService(
          buildProperties,
          gitProperties,
          resourceLoader,
          new ApplicationInfoProperties(new ApplicationInfoProperties.Sbom(false, "")));
    }
  }

  /** Marker subclass so the test can tell the application's bean from the library's default. */
  static class CustomApplicationInfoService extends ApplicationInfoService {

    CustomApplicationInfoService(
        final ObjectProvider<BuildProperties> buildProperties,
        final ObjectProvider<GitProperties> gitProperties,
        final ResourceLoader resourceLoader,
        final ApplicationInfoProperties properties) {
      super(buildProperties, gitProperties, resourceLoader, properties);
    }
  }
}
