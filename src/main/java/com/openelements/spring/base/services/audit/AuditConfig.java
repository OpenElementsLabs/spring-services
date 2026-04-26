package com.openelements.spring.base.services.audit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring auto-configuration for the audit-log feature.
 *
 * <p>Component-scans this package to register {@link AuditLogDataService}, {@link
 * AuditLogEventListener} and {@link AuditCleanupJob}, binds {@link AuditProperties}, and enables
 * Spring scheduling so the retention cleanup runs automatically.
 */
@Configuration
@ComponentScan
@AutoConfiguration
@EnableAutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(AuditProperties.class)
public class AuditConfig {}
