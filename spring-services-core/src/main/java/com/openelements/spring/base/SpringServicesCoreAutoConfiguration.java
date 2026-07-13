package com.openelements.spring.base;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Core auto-configuration that turns {@code spring-services-core} into a real Spring Boot starter.
 *
 * <p>Each optional feature module ({@code -slack}, {@code -search}, {@code -dbbackup}, {@code -email},
 * {@code -mcp}) ships its own {@code @AutoConfiguration} guarded by {@code @ConditionalOnClass}; this
 * class configures only the always-present core features.
 *
 * <p>Registered through {@code META-INF/spring/
 * org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so a consuming application only
 * needs the dependency on the classpath — no {@code @Import}, {@code @EntityScan} or
 * {@code @EnableJpaRepositories}.
 *
 * <p>The configuration {@link Import}s {@link FullSpringServiceConfig} (the aggregate of every
 * feature {@code @Configuration}) and a {@link PackageRegistrar} that appends the library root
 * package to {@link AutoConfigurationPackages}. Because the library entities live in the dedicated
 * {@code oe_spring_services} schema (see {@link com.openelements.spring.base.data.DbSchema}), they
 * never collide with the application's own tables.
 *
 * <p><strong>Ordering is binding, not incidental.</strong> The additive-scan mechanism only works if
 * the registrar has appended the library package <em>before</em> Hibernate's entity scan and Spring
 * Data's repository registrar read {@link AutoConfigurationPackages}. Both readers are themselves
 * auto-configuration-driven, and auto-configuration ordering is otherwise unspecified — therefore
 * this class declares {@link AutoConfigureBefore @AutoConfigureBefore} against
 * {@link HibernateJpaAutoConfiguration} and {@link JpaRepositoriesAutoConfiguration}. The guarantee
 * is proven by an integration test, not assumed.
 *
 * <p>{@link ConditionalOnClass @ConditionalOnClass(EntityManagerFactory.class)} keeps the starter
 * inert in an application that has no JPA on the classpath.
 *
 * <p>{@code @Import(FullSpringServiceConfig.class)} remains valid for consumers who prefer explicit
 * wiring or who exclude this auto-configuration; it simply becomes optional.
 */
@AutoConfiguration
@ConditionalOnClass(EntityManagerFactory.class)
@AutoConfigureBefore({HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@Import({FullSpringServiceConfig.class, SpringServicesCoreAutoConfiguration.PackageRegistrar.class})
public class SpringServicesCoreAutoConfiguration {

  /** Root package of the library — scanned for both entities and Spring Data repositories. */
  static final String LIBRARY_ROOT_PACKAGE = "com.openelements.spring.base";

  /**
   * Appends the library root package to {@link AutoConfigurationPackages} so Boot's entity scan and
   * Spring Data's repository scan (both default to {@code AutoConfigurationPackages}) cover the
   * application's package(s) <em>and</em> the library's.
   *
   * <p>Deliberately does <strong>not</strong> use {@code @EntityScan}: that would make
   * {@code EntityScanPackages} non-empty and thereby suppress Boot's default scan of the
   * application's own package. Appending to {@link AutoConfigurationPackages} keeps
   * {@code EntityScanPackages} empty, so the union of application and library packages is scanned.
   */
  static class PackageRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(
        final AnnotationMetadata importingClassMetadata, final BeanDefinitionRegistry registry) {
      AutoConfigurationPackages.register(registry, LIBRARY_ROOT_PACKAGE);
    }
  }
}
