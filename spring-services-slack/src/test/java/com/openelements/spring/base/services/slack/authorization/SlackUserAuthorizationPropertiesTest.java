package com.openelements.spring.base.services.slack.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("SlackUserAuthorizationProperties")
class SlackUserAuthorizationPropertiesTest {

  static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Binding.class);

  @Test
  @DisplayName("Is disabled by default, with the default path and every required value missing")
  void defaults() {
    runner.run(
        context -> {
          final SlackUserAuthorizationProperties properties =
              context.getBean(SlackUserAuthorizationProperties.class);
          assertThat(properties.enabled()).isFalse();
          assertThat(properties.path()).isEqualTo("/slack/oauth");
          assertThat(properties.missingProperties())
              .containsExactly(
                  "open-elements.slack.user-authorization.client-id",
                  "open-elements.slack.user-authorization.client-secret",
                  "open-elements.slack.user-authorization.public-base-url",
                  "open-elements.slack.user-authorization.token-encryption-key");
        });
  }

  @Test
  @DisplayName("Binds every value and reports nothing missing")
  void bindsAllValues() {
    runner
        .withPropertyValues(
            "open-elements.slack.user-authorization.enabled=true",
            "open-elements.slack.user-authorization.client-id=1234.5678",
            "open-elements.slack.user-authorization.client-secret=s3cr3t",
            "open-elements.slack.user-authorization.public-base-url=https://app.example.com/",
            "open-elements.slack.user-authorization.path=/integrations/slack-auth",
            "open-elements.slack.user-authorization.token-encryption-key=" + KEY)
        .run(
            context -> {
              final SlackUserAuthorizationProperties properties =
                  context.getBean(SlackUserAuthorizationProperties.class);
              assertThat(properties.enabled()).isTrue();
              assertThat(properties.missingProperties()).isEmpty();
              assertThat(properties.authorizationUrl())
                  .isEqualTo("https://app.example.com/integrations/slack-auth/authorize");
              assertThat(properties.redirectUri())
                  .isEqualTo("https://app.example.com/integrations/slack-auth/callback");
              assertThat(properties.decodedKey()).hasValueSatisfying(k -> assertThat(k).hasSize(32));
            });
  }

  @Test
  @DisplayName("Treats blank values as missing")
  void blankIsMissing() {
    final SlackUserAuthorizationProperties properties =
        new SlackUserAuthorizationProperties(true, " ", "", "  ", "/slack/oauth", " ");

    assertThat(properties.missingProperties()).hasSize(4);
    assertThat(properties.authorizationUrl()).isNull();
  }

  @Test
  @DisplayName("Reports a key that is no Base64 or not 32 bytes as invalid")
  void invalidKey() {
    final String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

    assertThat(withKey("not base64!").missingProperties())
        .containsExactly(
            "open-elements.slack.user-authorization.token-encryption-key"
                + " (invalid: must be Base64 of 32 bytes)");
    assertThat(withKey(shortKey).missingProperties()).hasSize(1);
    assertThat(withKey(shortKey).decodedKey()).isEmpty();
  }

  @Test
  @DisplayName("Masks the client secret and the key in toString")
  void masksSecrets() {
    final String text = withKey(KEY).toString();

    assertThat(text).contains("clientId=client").doesNotContain("s3cr3t").doesNotContain(KEY);
  }

  private static SlackUserAuthorizationProperties withKey(final String key) {
    return new SlackUserAuthorizationProperties(
        true, "client", "s3cr3t", "https://app.example.com", "/slack/oauth", key);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(SlackUserAuthorizationProperties.class)
  static class Binding {}
}
