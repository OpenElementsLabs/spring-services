package com.openelements.spring.base;

import com.openelements.spring.base.security.SecurityConfig;
import com.openelements.spring.base.services.apikey.ApiKeyConfig;
import com.openelements.spring.base.services.settings.SettingsConfig;
import com.openelements.spring.base.services.tag.TagConfig;
import com.openelements.spring.base.services.webhook.WebhookConfig;
import com.openelements.spring.base.tenant.TenantConfig;
import org.springframework.context.annotation.Import;

@Import({
  SecurityConfig.class,
  TenantConfig.class,
  ApiKeyConfig.class,
  SettingsConfig.class,
  TagConfig.class,
  WebhookConfig.class
})
public class FullSpringServiceConfig {}
