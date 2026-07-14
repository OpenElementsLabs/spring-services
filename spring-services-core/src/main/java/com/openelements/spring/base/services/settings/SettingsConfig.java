package com.openelements.spring.base.services.settings;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the settings feature.
 *
 * <p>Component-scans this package to register the settings service and repository.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = SettingsConfig.class)
public class SettingsConfig {

  /** Creates the configuration; the settings beans are registered via component scanning. */
  public SettingsConfig() {}
}
