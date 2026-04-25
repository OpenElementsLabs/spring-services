package com.openelements.spring.base.services.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.slack.api.RequestConfigurator;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import java.io.IOException;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

@DisplayName("SlackService")
class SlackServiceTest {

  private MethodsClient methodsClient;
  private SlackService slackService;

  @BeforeEach
  void setUp() {
    methodsClient = mock(MethodsClient.class);
    slackService = new SlackService(provider(methodsClient));
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<MethodsClient> provider(final MethodsClient client) {
    final ObjectProvider<MethodsClient> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(client);
    return provider;
  }

  private ChatPostMessageResponse okResponse() {
    final ChatPostMessageResponse response = new ChatPostMessageResponse();
    response.setOk(true);
    return response;
  }

  private ChatPostMessageResponse errorResponse(final String error) {
    final ChatPostMessageResponse response = new ChatPostMessageResponse();
    response.setOk(false);
    response.setError(error);
    return response;
  }

  @SuppressWarnings("unchecked")
  private RequestConfigurator<ChatPostMessageRequest.ChatPostMessageRequestBuilder>
      anyConfigurator() {
    return any(RequestConfigurator.class);
  }

  @Nested
  @DisplayName("sendMessage — happy path")
  class HappyPath {

    @Test
    @DisplayName("posts to channel by name")
    void sendsMessageToChannelByName() throws Exception {
      when(methodsClient.chatPostMessage(anyConfigurator())).thenReturn(okResponse());

      slackService.sendMessage("#general", "Hello World");

      final ArgumentCaptor<
              RequestConfigurator<ChatPostMessageRequest.ChatPostMessageRequestBuilder>>
          captor = captureRequest();
      final ChatPostMessageRequest request = buildRequest(captor.getValue());
      assertThat(request.getChannel()).isEqualTo("#general");
      assertThat(request.getText()).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("posts to channel by ID")
    void sendsMessageToChannelById() throws Exception {
      when(methodsClient.chatPostMessage(anyConfigurator())).thenReturn(okResponse());

      slackService.sendMessage("C01234ABCDE", "Hello World");

      final ChatPostMessageRequest request = buildRequest(captureRequest().getValue());
      assertThat(request.getChannel()).isEqualTo("C01234ABCDE");
      assertThat(request.getText()).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("forwards messages containing links unchanged")
    void sendsMessageContainingLink() throws Exception {
      when(methodsClient.chatPostMessage(anyConfigurator())).thenReturn(okResponse());

      slackService.sendMessage("#general", "Check https://example.com for details");

      final ChatPostMessageRequest request = buildRequest(captureRequest().getValue());
      assertThat(request.getText()).isEqualTo("Check https://example.com for details");
    }
  }

  @Nested
  @DisplayName("sendMessage — error cases")
  class ErrorCases {

    @Test
    @DisplayName("throws when no token is configured")
    void throwsWhenNoToken() {
      final SlackService unconfigured = new SlackService(provider(null));

      assertThatThrownBy(() -> unconfigured.sendMessage("#general", "Hello"))
          .isInstanceOf(SlackException.class)
          .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("throws when Slack API responds with channel_not_found")
    void throwsOnApiError() throws Exception {
      when(methodsClient.chatPostMessage(anyConfigurator()))
          .thenReturn(errorResponse("channel_not_found"));

      assertThatThrownBy(() -> slackService.sendMessage("#nonexistent", "Hello"))
          .isInstanceOf(SlackException.class)
          .hasMessageContaining("channel_not_found");
    }

    @Test
    @DisplayName("throws when token is revoked / authentication fails")
    void throwsOnRevokedToken() throws Exception {
      when(methodsClient.chatPostMessage(anyConfigurator()))
          .thenThrow(slackApiException("invalid_auth"));

      assertThatThrownBy(() -> slackService.sendMessage("#general", "Hello"))
          .isInstanceOf(SlackException.class);
    }

    @Test
    @DisplayName("wraps IOException as SlackException with cause")
    void wrapsIoExceptionWithCause() throws Exception {
      final IOException ioCause = new IOException("connection refused");
      when(methodsClient.chatPostMessage(anyConfigurator())).thenThrow(ioCause);

      assertThatThrownBy(() -> slackService.sendMessage("#general", "Hello"))
          .isInstanceOf(SlackException.class)
          .hasCause(ioCause);
    }
  }

  @Nested
  @DisplayName("sendMessage — input validation")
  class InputValidation {

    @Test
    @DisplayName("rejects null channel")
    void rejectsNullChannel() {
      assertThatThrownBy(() -> slackService.sendMessage(null, "Hello"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects null text")
    void rejectsNullText() {
      assertThatThrownBy(() -> slackService.sendMessage("#general", null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<RequestConfigurator<ChatPostMessageRequest.ChatPostMessageRequestBuilder>>
      captureRequest() {
    final ArgumentCaptor<RequestConfigurator<ChatPostMessageRequest.ChatPostMessageRequestBuilder>>
        captor = ArgumentCaptor.forClass(RequestConfigurator.class);
    try {
      org.mockito.Mockito.verify(methodsClient).chatPostMessage(captor.capture());
    } catch (final Exception e) {
      throw new AssertionError(e);
    }
    return captor;
  }

  private static ChatPostMessageRequest buildRequest(
      final RequestConfigurator<ChatPostMessageRequest.ChatPostMessageRequestBuilder>
          configurator) {
    return configurator.configure(ChatPostMessageRequest.builder()).build();
  }

  private static SlackApiException slackApiException(final String error) {
    final ChatPostMessageResponse body = new ChatPostMessageResponse();
    body.setOk(false);
    body.setError(error);
    final Request httpRequest =
        new Request.Builder().url("https://slack.com/api/chat.postMessage").build();
    final Response httpResponse =
        new Response.Builder()
            .request(httpRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(ResponseBody.create("{}", okhttp3.MediaType.parse("application/json")))
            .build();
    return new SlackApiException(httpResponse, "{\"ok\":false,\"error\":\"" + error + "\"}");
  }
}
