package com.openelements.spring.base.services.tag;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the tag feature.
 *
 * <p>Component-scans this package so that {@link TagDataService} and {@link TagRepository} are
 * picked up. Imported by {@link com.openelements.spring.base.FullSpringServiceConfig} as part of
 * the full platform setup.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = TagConfig.class)
public class TagConfig {

  /** Creates the configuration; the tag beans are registered via component scanning. */
  public TagConfig() {}
}
