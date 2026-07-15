package com.openelements.spring.base.services.dbbackup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the db-backup-service client.
 *
 * <p>Component-scans this package so that {@link DbBackupClient} is picked up, and binds {@link
 * DbBackupProperties}. Imported by {@link com.openelements.spring.base.FullSpringServiceConfig} as
 * part of the full platform setup.
 *
 * <p>This is a plain {@code @Configuration} (not {@code @AutoConfiguration}) for the same reason as
 * the {@code SearchConfig} of the search module: a host application's {@code @ComponentScan}
 * reliably picks up plain configurations but excludes auto-configurations, so the properties are
 * always registered alongside the client.
 *
 * <p>The feature is <strong>opt-in and disabled by default</strong>: none of these beans are created
 * unless {@code openelements.db-backup.enabled=true}. This avoids forcing a db-backup configuration
 * on consumers that do not use the sidecar. When enabled, {@code openelements.db-backup.base-url} is
 * required (see {@link DbBackupProperties}) — a missing base URL fails startup rather than defaulting
 * to {@code localhost}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "openelements.db-backup", name = "enabled", havingValue = "true")
@ComponentScan(basePackageClasses = DbBackupConfig.class)
@EnableConfigurationProperties(DbBackupProperties.class)
public class DbBackupConfig {

  /** Creates the configuration. */
  public DbBackupConfig() {}
}
