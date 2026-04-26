package com.openelements.spring.base.services.audit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring configuration for the audit-log feature.
 *
 * <p>Component-scans this package to register {@link AuditLogDataService}, {@link
 * AuditLogEventListener}, {@link AuditCleanupJob} and {@link AuditProperties}, and enables Spring
 * scheduling so the retention cleanup runs automatically.
 */
@Configuration
@ComponentScan
@AutoConfiguration
@EnableAutoConfiguration
@EnableScheduling
public class AuditConfig {}
