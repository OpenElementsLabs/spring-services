package com.openelements.spring.base.services.user;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the user feature.
 *
 * <p>Component-scans this package to register the user service, repository and provisioning beans.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = UserConfig.class)
public class UserConfig {

  /** Creates the configuration; the user beans are registered via component scanning. */
  public UserConfig() {}
}
