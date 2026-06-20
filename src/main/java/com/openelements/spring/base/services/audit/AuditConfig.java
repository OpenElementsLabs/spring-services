package com.openelements.spring.base.services.audit;

import com.openelements.spring.base.services.user.UserConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring configuration for the audit-log feature.
 *
 * <p>Component-scans this package to register {@link AuditLogDataService}, {@link
 * AuditLogEventListener}, {@link AuditCleanupJob} and {@link AuditProperties}, and enables Spring
 * scheduling so the retention cleanup runs automatically.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AuditConfig.class)
@EnableScheduling
@Import(UserConfig.class)
public class AuditConfig {}
