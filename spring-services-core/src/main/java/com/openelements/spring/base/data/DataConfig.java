package com.openelements.spring.base.data;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the generic data layer.
 *
 * <p>Most of this package consists of abstractions the application subclasses
 * ({@link AbstractEntity}, {@link AbstractDbBackedDataService}) and therefore needs no bean
 * registration. The only ready-to-use bean the package contributes is {@link DbHealthService}.
 *
 * <p>The bean is declared explicitly rather than via {@code @ComponentScan} + {@code @Service}.
 * A single bean does not justify a package scan, and an explicit {@code @Bean} method cannot be
 * registered twice when a consumer both imports this configuration and scans the library package.
 */
@Configuration(proxyBeanMethods = false)
public class DataConfig {

  /** Creates the configuration; the data-layer beans are declared as {@code @Bean} methods. */
  public DataConfig() {}

  /**
   * Registers the database reachability check for the application's {@link DataSource}.
   *
   * @param dataSource the data source to probe
   * @return the database health service
   */
  @Bean
  public DbHealthService dbHealthService(final DataSource dataSource) {
    return new DbHealthService(dataSource);
  }
}
