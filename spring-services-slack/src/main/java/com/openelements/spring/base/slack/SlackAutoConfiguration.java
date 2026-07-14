package com.openelements.spring.base.slack;

import com.openelements.spring.base.services.slack.SlackConfig;
import com.slack.api.Slack;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the optional Slack messaging feature.
 *
 * <p>Registered through this module's
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so the
 * feature self-activates when {@code spring-services-slack} is on the classpath and stays absent
 * otherwise. {@link ConditionalOnClass @ConditionalOnClass(Slack.class)} keeps it inert if the
 * {@code slack-api-client} dependency is not present, so a bundle that ships this class without the
 * SDK causes no {@code NoClassDefFoundError}.
 *
 * <p>Lives outside the {@code services.slack} package on purpose: {@link SlackConfig} declares a
 * {@code @ComponentScan} over its own package, and an auto-configuration co-located there would be
 * swept up by that scan and registered twice. Delegates the actual bean wiring to
 * {@link SlackConfig}, which remains a plain {@code @Configuration} consumers can still
 * {@code @Import} explicitly.
 */
@AutoConfiguration
@ConditionalOnClass(Slack.class)
@Import(SlackConfig.class)
public class SlackAutoConfiguration {

  /** Creates the auto-configuration. */
  public SlackAutoConfiguration() {}
}
