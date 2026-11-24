package com.openelements.spring.base.services.slack.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("SlackEventsProperties")
class SlackEventsPropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Test
  @DisplayName("Is disabled, has no secret and uses /slack/events when nothing is configured")
  void defaults() {
    runner.run(
        context -> {
          final SlackEventsProperties properties = context.getBean(SlackEventsProperties.class);
          assertThat(properties.enabled()).isFalse();
          assertThat(properties.signingSecret()).isNull();
          assertThat(properties.path()).isEqualTo("/slack/events");
          assertThat(properties.teamId()).isNull();
          assertThat(properties.appId()).isNull();
        });
  }

  @Test
  @DisplayName("Binds every value from open-elements.slack.events.*")
  void bindsAllValues() {
    runner
        .withPropertyValues(
            "open-elements.slack.events.enabled=true",
            "open-elements.slack.events.signing-secret=secret",
            "open-elements.slack.events.path=/integrations/slack",
            "open-elements.slack.events.team-id=T123",
            "open-elements.slack.events.app-id=A456")
        .run(
            context -> {
              final SlackEventsProperties properties = context.getBean(SlackEventsProperties.class);
              assertThat(properties.enabled()).isTrue();
              assertThat(properties.signingSecret()).isEqualTo("secret");
              assertThat(properties.path()).isEqualTo("/integrations/slack");
              assertThat(properties.teamId()).isEqualTo("T123");
              assertThat(properties.appId()).isEqualTo("A456");
            });
  }

  @Test
  @DisplayName("Treats a blank team-id and app-id as unset")
  void blankRestrictionsAreUnset() {
    runner
        .withPropertyValues(
            "open-elements.slack.events.team-id=", "open-elements.slack.events.app-id=  ")
        .run(
            context -> {
              final SlackEventsProperties properties = context.getBean(SlackEventsProperties.class);
              assertThat(properties.teamId()).isNull();
              assertThat(properties.appId()).isNull();
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(SlackEventsProperties.class)
  static class PropertiesConfiguration {}
}
