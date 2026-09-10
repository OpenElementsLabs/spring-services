package com.openelements.spring.base.info;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/**
 * Auto-configuration that contributes the {@link ApplicationInfoService}.
 *
 * <p>Registered as a second entry in {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so it activates
 * whenever {@code spring-services-core} is on the classpath. Unlike {@link
 * com.openelements.spring.base.SpringServicesCoreAutoConfiguration} it is <strong>not</strong>
 * guarded by {@code @ConditionalOnClass(EntityManagerFactory.class)} and is not imported by {@code
 * FullSpringServiceConfig}: application info has nothing to do with JPA and must work in an
 * application that has no persistence layer.
 *
 * <p>Ordered {@code after} {@link ProjectInfoAutoConfiguration} so that {@link BuildProperties} and
 * {@link GitProperties} — which that configuration contributes when {@code build-info.properties} /
 * {@code git.properties} exist — are already available when the service reads them in its
 * constructor.
 *
 * <p>The service bean is guarded by {@link ConditionalOnMissingBean}, so an application can supply
 * its own {@link ApplicationInfoService} and the library's default backs off.
 */
@AutoConfiguration(after = ProjectInfoAutoConfiguration.class)
@EnableConfigurationProperties(ApplicationInfoProperties.class)
public class ApplicationInfoAutoConfiguration {

  /** Creates the auto-configuration; the service is contributed by {@link #applicationInfoService}. */
  public ApplicationInfoAutoConfiguration() {}

  /**
   * Contributes the default {@link ApplicationInfoService}, unless the application declares its own.
   *
   * @param buildProperties provider for the optional {@link BuildProperties} bean
   * @param gitProperties provider for the optional {@link GitProperties} bean
   * @param resourceLoader loader used to resolve the SBOM resource
   * @param properties the SBOM-reading configuration
   * @return the application-info service
   */
  @Bean
  @ConditionalOnMissingBean
  public ApplicationInfoService applicationInfoService(
      final ObjectProvider<BuildProperties> buildProperties,
      final ObjectProvider<GitProperties> gitProperties,
      final ResourceLoader resourceLoader,
      final ApplicationInfoProperties properties) {
    return new ApplicationInfoService(buildProperties, gitProperties, resourceLoader, properties);
  }
}
