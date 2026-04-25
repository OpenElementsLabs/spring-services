package com.openelements.spring.base;

import com.openelements.spring.base.security.SecurityConfig;
import com.openelements.spring.base.services.apikey.ApiKeyConfig;
import com.openelements.spring.base.services.settings.SettingsConfig;
import com.openelements.spring.base.services.slack.SlackConfig;
import com.openelements.spring.base.services.tag.TagConfig;
import com.openelements.spring.base.services.webhook.WebhookConfig;
import com.openelements.spring.base.tenant.TenantConfig;
import org.springframework.context.annotation.Import;

/**
 * Aggregate Spring configuration that imports every feature-level configuration shipped with {@code
 * spring-services}.
 *
 * <p>Importing this single class is the easiest way to get the full platform up and running:
 *
 * <pre>{@code
 * @SpringBootApplication
 * @Import(FullSpringServiceConfig.class)
 * public class MyApplication { ... }
 * }</pre>
 *
 * <p>Applications that only need a subset of the platform (e.g. tags but no multi-tenancy, or
 * webhooks without API keys) can ignore this class and import the individual feature configurations
 * directly:
 *
 * <ul>
 *   <li>{@link SecurityConfig} — JWT and API-key authentication.
 *   <li>{@link TenantConfig} — multi-tenancy support (entities, repositories, current-tenant
 *       resolution).
 *   <li>{@link ApiKeyConfig} — API-key data layer (entity, repository, data service).
 *   <li>{@link SettingsConfig} — generic key/value settings store.
 *   <li>{@link TagConfig} — taggable-entity support.
 *   <li>{@link WebhookConfig} — outbound webhook subscriptions and dispatching.
 *   <li>{@link SlackConfig} — Slack messaging service for posting plain-text messages to channels.
 * </ul>
 */
@Import({
  SecurityConfig.class,
  TenantConfig.class,
  ApiKeyConfig.class,
  SettingsConfig.class,
  TagConfig.class,
  WebhookConfig.class,
  SlackConfig.class
})
public class FullSpringServiceConfig {}
