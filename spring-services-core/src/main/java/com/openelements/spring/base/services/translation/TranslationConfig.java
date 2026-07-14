package com.openelements.spring.base.services.translation;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the translation feature.
 *
 * <p>Component-scans this package to register the translation-related beans.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = TranslationConfig.class)
public class TranslationConfig {

  /** Creates the configuration; the translation beans are registered via component scanning. */
  public TranslationConfig() {}
}
