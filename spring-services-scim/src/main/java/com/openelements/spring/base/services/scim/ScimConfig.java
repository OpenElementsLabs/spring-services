package com.openelements.spring.base.services.scim;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Feature configuration for the SCIM 2.0 Users provider.
 *
 * <p>Component-scans this package so the SCIM security chain, token filter, controllers, service,
 * error advice, content-type converter, and the service-principal initializer are all registered,
 * and binds {@link ScimProperties}. Activated by {@code ScimAutoConfiguration} only when
 * {@code openelements.scim.token} is set; consumers may also {@code @Import} it explicitly.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = ScimConfig.class)
@EnableConfigurationProperties(ScimProperties.class)
public class ScimConfig {

  /** Creates the SCIM feature configuration; beans are contributed by the component scan. */
  public ScimConfig() {}
}
