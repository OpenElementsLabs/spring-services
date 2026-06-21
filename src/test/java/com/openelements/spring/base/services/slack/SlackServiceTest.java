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

/**
 * Mockito-style unit tests for {@link SlackService}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The behaviour of {@code sendMessage(channel, text)} across its three argument shapes (channel
 * name like {@code #general}, channel ID like {@code C01234ABCDE}, text containing a link) and
 * its error contract. Specifically:
 *
 * <ul>
 *   <li>The request configurator passed to {@link MethodsClient#chatPostMessage} forwards the
 *       caller's {@code channel} and {@code text} unchanged — captured and rebuilt to inspect.
 *   <li>An unconfigured service (no token, {@code ObjectProvider} yields {@code null}) fails fast
 *       with {@link SlackException} carrying the "not configured" hint.
 *   <li>A Slack API {@code ok=false} response is converted to {@link SlackException} with the
 *       Slack error code surfaced in the message ({@code channel_not_found}, {@code
 *       invalid_auth}).
 *   <li>An {@link IOException} from the HTTP layer is wrapped as {@link SlackException} with the
 *       original cause preserved (operators need it for triage).
 *   <li>{@code null} channel or text is rejected with {@link NullPointerException} before any
 *       HTTP call — caller bugs surface immediately.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 with Mockito, no Spring context. The {@link SlackService} is instantiated
 * directly; the API client is faked.
 *
 * <p><b>Mock-Audit.</b> Two mocks:
 *
 * <ul>
 *   <li>{@code mock(MethodsClient.class)} — the official Slack SDK client. A real client would
 *       require network access to {@code slack.com} and a live bot token; that is the
 *       responsibility of end-to-end smoke tests, not these unit tests. The mock lets us assert
 *       what request configurator was passed (via {@code ArgumentCaptor}) and inject crafted
 *       responses / exceptions for the error paths.
 *   <li>{@code mock(ObjectProvider.class)} — the Spring {@link ObjectProvider} that supplies the
 *       optional {@link MethodsClient}. Mocking lets a single test switch between the "configured"
 *       and "not configured" states without standing up a Spring context. {@link SlackConfigTest}
 *       covers the real {@code ObjectProvider} wiring inside a context.
 * </ul>
 *
 * <p>A synthetic {@link SlackApiException} is built by constructing real OkHttp {@link Response}
 * / {@link Request} objects (no mocking of OkHttp) — the SDK's exception constructor demands a
 * real {@code Response}, so faking that surface would be more brittle than building one.
 */
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
    @DisplayName("sendMessage forwards a channel name like #general unchanged into the chat.postMessage request.")
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
    @DisplayName("sendMessage forwards a Slack channel ID like C01234ABCDE unchanged into the chat.postMessage request.")
    void sendsMessageToChannelById() throws Exception {
      when(methodsClient.chatPostMessage(anyConfigurator())).thenReturn(okResponse());

      slackService.sendMessage("C01234ABCDE", "Hello World");

      final ChatPostMessageRequest request = buildRequest(captureRequest().getValue());
      assertThat(request.getChannel()).isEqualTo("C01234ABCDE");
      assertThat(request.getText()).isEqualTo("Hello World");
    }

    /**
     * Pins that {@code SlackService} does no link rewriting, escaping, or wrapping of message
     * text — Slack itself parses URLs server-side. Future "helpful" pre-processing would silently
     * change message shapes for downstream consumers.
     */
    @Test
    @DisplayName("Message text containing URLs is forwarded byte-for-byte — no client-side link rewriting.")
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
    @DisplayName("With no MethodsClient available (token unconfigured), sendMessage fails fast with SlackException carrying the \"not configured\" hint.")
    void throwsWhenNoToken() {
      final SlackService unconfigured = new SlackService(provider(null));

      assertThatThrownBy(() -> unconfigured.sendMessage("#general", "Hello"))
          .isInstanceOf(SlackException.class)
          .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("A Slack API ok=false response (e.g. channel_not_found) is translated to SlackException with the API error code in the message.")
    void throwsOnApiError() throws Exception {
      when(methodsClient.chatPostMessage(anyConfigurator()))
          .thenReturn(errorResponse("channel_not_found"));

      assertThatThrownBy(() -> slackService.sendMessage("#nonexistent", "Hello"))
          .isInstanceOf(SlackException.class)
          .hasMessageContaining("channel_not_found");
    }

    @Test
    @DisplayName("A SlackApiException from the SDK (e.g. invalid_auth on revoked token) is re-thrown as SlackException.")
    void throwsOnRevokedToken() throws Exception {
      when(methodsClient.chatPostMessage(anyConfigurator()))
          .thenThrow(slackApiException("invalid_auth"));

      assertThatThrownBy(() -> slackService.sendMessage("#general", "Hello"))
          .isInstanceOf(SlackException.class);
    }

    /**
     * Confirms the network-error path preserves diagnostic context: the {@link IOException}
     * thrown by the HTTP transport is set as the {@code cause} on the {@link SlackException},
     * so log-and-trace tooling can attribute the failure to (e.g.) {@code connection refused}.
     */
    @Test
    @DisplayName("An IOException from the HTTP transport is wrapped as SlackException with the original IOException as cause.")
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
    @DisplayName("sendMessage(null, text) throws NullPointerException — no Slack request is issued.")
    void rejectsNullChannel() {
      assertThatThrownBy(() -> slackService.sendMessage(null, "Hello"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("sendMessage(channel, null) throws NullPointerException — no Slack request is issued.")
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
