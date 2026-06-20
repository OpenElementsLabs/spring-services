package com.openelements.spring.base.services.dbbackup;

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
 * {@link com.openelements.spring.base.services.search.SearchConfig}: a host application's
 * {@code @ComponentScan} reliably picks up plain configurations but excludes auto-configurations,
 * so the properties are always registered alongside the client.
 *
 * <p>The feature is inert until the consuming application configures
 * {@code openelements.db-backup.base-url} and {@code openelements.db-backup.api-token}.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = DbBackupConfig.class)
@EnableConfigurationProperties(DbBackupProperties.class)
public class DbBackupConfig {}
