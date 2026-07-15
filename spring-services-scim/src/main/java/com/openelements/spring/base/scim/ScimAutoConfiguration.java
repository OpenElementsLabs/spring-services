package com.openelements.spring.base.scim;

import com.openelements.spring.base.services.scim.ScimConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the optional SCIM 2.0 Users provider.
 *
 * <p>Registered through this module's
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}. Unlike
 * the other feature modules it is gated on a property rather than a class:
 * {@link ConditionalOnProperty @ConditionalOnProperty(openelements.scim.token)} keeps the entire
 * feature — the isolated {@code /scim/v2/**} security chain, controllers and beans — inert unless a
 * static SCIM token is configured, so a consumer that does not use SCIM ships nothing extra.
 *
 * <p>Lives outside the {@code services.scim} package on purpose: {@link ScimConfig} declares a
 * {@code @ComponentScan} over its own package, and an auto-configuration co-located there would be
 * swept up by that scan and registered twice.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "openelements.scim", name = "token")
@Import(ScimConfig.class)
public class ScimAutoConfiguration {

  /** Creates the SCIM auto-configuration; wiring is delegated to {@link ScimConfig}. */
  public ScimAutoConfiguration() {}
}
