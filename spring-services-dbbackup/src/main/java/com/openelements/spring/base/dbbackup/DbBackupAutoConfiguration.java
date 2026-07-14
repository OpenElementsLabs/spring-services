package com.openelements.spring.base.dbbackup;

import com.openelements.spring.base.services.dbbackup.DbBackupConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the optional db-backup-service sidecar client feature.
 *
 * <p>Registered through this module's
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so the
 * feature self-activates when {@code spring-services-dbbackup} is on the classpath. The module
 * carries no extra third-party dependency (it talks to the sidecar over Spring's {@code RestClient}
 * via core), so there is no heavy-dependency class to guard on; runtime opt-in stays governed by
 * {@link DbBackupConfig}'s existing {@code @ConditionalOnProperty(openelements.db-backup.enabled)}.
 *
 * <p>Lives outside the {@code services.dbbackup} package on purpose: {@link DbBackupConfig} declares
 * a {@code @ComponentScan} over its own package, and an auto-configuration co-located there would be
 * swept up by that scan and registered twice.
 */
@AutoConfiguration
@Import(DbBackupConfig.class)
public class DbBackupAutoConfiguration {

  /** Creates the auto-configuration. */
  public DbBackupAutoConfiguration() {}
}
