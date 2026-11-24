package com.openelements.spring.base.services.slack;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.openelements.spring.base.services.slack.authorization.InMemorySlackUserTokenStore;
import com.openelements.spring.base.services.slack.authorization.SlackUserAuthorizationProperties;
import com.openelements.spring.base.services.slack.authorization.SlackUserTokenStore;
import com.openelements.spring.base.services.slack.events.SlackMessageKind;
import com.openelements.spring.base.services.slack.events.SlackMessageReceivedEvent;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * Editing a user's message through {@link SlackService}, with the Slack Web API served by a local
 * WireMock server.
 *
 * <p>No bot token is configured in any of these tests: editing works with the user's token alone.
 * Tokens come from an in-memory store; the real store is covered by {@code
 * JdbcSlackUserTokenStoreTest}.
 */
@DisplayName("SlackService.updateUserMessage")
class SlackServiceUserMessageTest {

  private static final String AUTHORIZATION_URL = "https://app.example.com/slack/oauth/authorize";

  private static final SlackUserAuthorizationProperties CONFIGURED =
      new SlackUserAuthorizationProperties(
          true,
          "1234.5678",
          "client-secret",
          "https://app.example.com",
          SlackUserAuthorizationProperties.DEFAULT_PATH,
          Base64.getEncoder().encodeToString(new byte[32]));

  private static WireMockServer slackApi;

  private final InMemorySlackUserTokenStore store = new InMemorySlackUserTokenStore();

  private SlackService service;

  @BeforeAll
  static void startSlackApi() {
    slackApi = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    slackApi.start();
  }

  @AfterAll
  static void stopSlackApi() {
    slackApi.stop();
  }

  @BeforeEach
  void setUp() {
    slackApi.resetAll();
    service = service(CONFIGURED, store);
  }

  @Test
  @DisplayName("Updates the message with the user's token, the new text and no blocks")
  void updatesWithUserToken() {
    store.save("T1", "U1", "xoxp-user-1");
    slackApi.stubFor(post(urlEqualTo("/api/chat.update")).willReturn(okJson("{\"ok\":true}")));

    service.updateUserMessage(message("T1", "U1"), "Hallo\n\nHello");

    slackApi.verify(
        postRequestedFor(urlEqualTo("/api/chat.update"))
            .withHeader("Authorization", equalTo("Bearer xoxp-user-1"))
            .withFormParam("channel", equalTo("C1"))
            .withFormParam("ts", equalTo("1700000000.000100"))
            .withFormParam("text", equalTo("Hallo\n\nHello"))
            .withFormParam("blocks", equalTo("[]")));
  }

  @Test
  @DisplayName("Asks for authorization without calling Slack when no token is stored")
  void noToken() {
    assertThatThrownBy(() -> service.updateUserMessage(message("T1", "U1"), "text"))
        .isInstanceOfSatisfying(
            SlackUserAuthorizationRequiredException.class,
            e -> assertThat(e.authorizationUrl()).isEqualTo(AUTHORIZATION_URL))
        .isInstanceOf(SlackException.class);
    assertThat(slackApi.getAllServeEvents()).isEmpty();
  }

  @Test
  @DisplayName("Keeps tokens apart per workspace and user")
  void separatedPerWorkspaceAndUser() {
    store.save("T1", "U1", "xoxp-user-1");

    assertThatThrownBy(() -> service.updateUserMessage(message("T2", "U1"), "text"))
        .isInstanceOf(SlackUserAuthorizationRequiredException.class);
    assertThatThrownBy(() -> service.updateUserMessage(message("T1", "U2"), "text"))
        .isInstanceOf(SlackUserAuthorizationRequiredException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"token_revoked", "invalid_auth", "account_inactive", "token_expired"})
  @DisplayName("Removes a token Slack rejects and asks for authorization again")
  void rejectedToken(final String error) {
    store.save("T1", "U1", "xoxp-user-1");
    slackApi.stubFor(
        post(urlEqualTo("/api/chat.update"))
            .willReturn(okJson("{\"ok\":false,\"error\":\"" + error + "\"}")));

    assertThatThrownBy(() -> service.updateUserMessage(message("T1", "U1"), "text"))
        .isInstanceOf(SlackUserAuthorizationRequiredException.class);
    assertThat(store.find("T1", "U1")).isEmpty();
    assertThat(service.isUserAuthorized("T1", "U1")).isFalse();
  }

  @Test
  @DisplayName("Keeps the token on another Slack error and names the error")
  void otherSlackError() {
    store.save("T1", "U1", "xoxp-user-1");
    slackApi.stubFor(
        post(urlEqualTo("/api/chat.update"))
            .willReturn(okJson("{\"ok\":false,\"error\":\"message_not_found\"}")));

    assertThatThrownBy(() -> service.updateUserMessage(message("T1", "U1"), "text"))
        .isExactlyInstanceOf(SlackException.class)
        .hasMessageContaining("message_not_found");
    assertThat(store.find("T1", "U1")).contains("xoxp-user-1");
  }

  @Test
  @DisplayName("Keeps the token on a network error")
  void networkError() {
    store.save("T1", "U1", "xoxp-user-1");
    slackApi.stubFor(
        post(urlEqualTo("/api/chat.update"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertThatThrownBy(() -> service.updateUserMessage(message("T1", "U1"), "text"))
        .isExactlyInstanceOf(SlackException.class)
        .hasCauseInstanceOf(java.io.IOException.class);
    assertThat(store.find("T1", "U1")).contains("xoxp-user-1");
  }

  @Test
  @DisplayName("Rejects null arguments before calling Slack")
  void nullArguments() {
    assertThatThrownBy(() -> service.updateUserMessage(null, "text"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.updateUserMessage(message("T1", "U1"), null))
        .isInstanceOf(NullPointerException.class);
    assertThat(slackApi.getAllServeEvents()).isEmpty();
  }

  @Test
  @DisplayName("Reports whether a user authorized and returns the authorization link")
  void statusAndLink() {
    store.save("T1", "U1", "xoxp-user-1");

    assertThat(service.isUserAuthorized("T1", "U1")).isTrue();
    assertThat(service.isUserAuthorized("T1", "U2")).isFalse();
    assertThat(service.userAuthorizationUrl()).isEqualTo(AUTHORIZATION_URL);
  }

  @Nested
  @DisplayName("Without the feature")
  class WithoutFeature {

    @Test
    @DisplayName("Fails with a hint to the enabling property while the feature is not enabled")
    void notEnabled() {
      final SlackService disabled = service(null, null);

      assertThatThrownBy(() -> disabled.updateUserMessage(message("T1", "U1"), "text"))
          .isExactlyInstanceOf(SlackException.class)
          .hasMessageContaining("open-elements.slack.user-authorization.enabled");
      assertThatThrownBy(() -> disabled.userAuthorizationUrl())
          .isExactlyInstanceOf(SlackException.class);
      assertThat(slackApi.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("Fails naming the missing properties while the feature is not configured")
    void notConfigured() {
      final SlackService notConfigured =
          service(
              new SlackUserAuthorizationProperties(
                  true, null, null, null, SlackUserAuthorizationProperties.DEFAULT_PATH, null),
              null);

      assertThatThrownBy(() -> notConfigured.updateUserMessage(message("T1", "U1"), "text"))
          .isExactlyInstanceOf(SlackException.class)
          .hasMessageContaining("client-id")
          .hasMessageContaining("token-encryption-key");
      assertThatThrownBy(() -> notConfigured.isUserAuthorized("T1", "U1"))
          .isExactlyInstanceOf(SlackException.class);
    }
  }

  private static SlackService service(
      final SlackUserAuthorizationProperties properties, final SlackUserTokenStore store) {
    final com.slack.api.SlackConfig config = new com.slack.api.SlackConfig();
    config.setMethodsEndpointUrlPrefix(slackApi.baseUrl() + "/api/");
    final StaticListableBeanFactory beans = new StaticListableBeanFactory();
    if (properties != null) {
      beans.addBean("properties", properties);
    }
    if (store != null) {
      beans.addBean("store", store);
    }
    return new SlackService(
        beans.getBeanProvider(MethodsClient.class),
        Slack.getInstance(config),
        beans.getBeanProvider(SlackUserAuthorizationProperties.class),
        beans.getBeanProvider(SlackUserTokenStore.class));
  }

  private static SlackMessageReceivedEvent message(final String teamId, final String userId) {
    return new SlackMessageReceivedEvent(
        SlackMessageKind.MESSAGE,
        "Ev1",
        teamId,
        "C1",
        "channel",
        userId,
        "Hallo",
        "1700000000.000100",
        null,
        Instant.ofEpochSecond(1_700_000_000L));
  }
}
