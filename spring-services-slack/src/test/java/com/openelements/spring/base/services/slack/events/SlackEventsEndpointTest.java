package com.openelements.spring.base.services.slack.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.problem.ProblemFactory;
import com.openelements.spring.base.services.slack.authorization.InMemorySlackUserTokenStore;
import com.openelements.spring.base.services.slack.authorization.SlackUserTokenStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The endpoint without HTTP stack, for the one path {@code SlackEventsWebTest} cannot reach: MockMvc
 * always sends a {@code Content-Length}, so an oversized body is rejected before it is read. A
 * chunked request has no such header, and the limit must hold while reading.
 */
@DisplayName("SlackEventsEndpoint")
class SlackEventsEndpointTest {

  private static final String SECRET = "endpoint-test-secret";

  private static final long NOW = 1_800_000_000L;

  private final List<Object> published = new ArrayList<>();

  private final InMemorySlackUserTokenStore userTokens = new InMemorySlackUserTokenStore();

  private final SlackEventsEndpoint endpoint = endpoint(new StaticListableBeanFactory());

  private SlackEventsEndpoint endpoint(final StaticListableBeanFactory beans) {
    return new SlackEventsEndpoint(
        new SlackSignatureVerifier(SECRET, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC)),
        new SlackEventsProperties(true, SECRET, SlackEventsProperties.DEFAULT_PATH, null, null),
        new SlackEventDeduplicator(Clock.systemUTC(), Duration.ofHours(1)),
        published::add,
        Runnable::run,
        beans.getBeanProvider(ProblemFactory.class),
        beans.getBeanProvider(SlackUserTokenStore.class));
  }

  @Test
  @DisplayName("tokens_revoked removes the user tokens of the listed users only and publishes nothing")
  void tokensRevokedRemovesListedUserTokens() throws Exception {
    userTokens.save("T061EG9R6", "U2147483697", "xoxp-revoked");
    userTokens.save("T061EG9R6", "U2147483698", "xoxp-kept");
    final StaticListableBeanFactory beans = new StaticListableBeanFactory();
    beans.addBean("userTokens", userTokens);

    final ServerResponse response =
        endpoint(beans).handle(request(SlackFixtures.load("tokens-revoked.json")));

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
    assertThat(userTokens.find("T061EG9R6", "U2147483697")).isEmpty();
    assertThat(userTokens.find("T061EG9R6", "U2147483698")).contains("xoxp-kept");
    assertThat(published).isEmpty();
  }

  @Test
  @DisplayName("tokens_revoked is acknowledged without a user token store")
  void tokensRevokedWithoutStore() throws Exception {
    final ServerResponse response = endpoint.handle(request(SlackFixtures.load("tokens-revoked.json")));

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
    assertThat(published).isEmpty();
  }

  @Test
  @DisplayName("Rejects a body over 1 MiB without Content-Length while reading it")
  void rejectsOversizedBodyWithoutContentLength() throws Exception {
    final byte[] body = new byte[SlackEventsEndpoint.MAX_BODY_BYTES + 1];

    final ServerResponse response = endpoint.handle(request(body));

    assertThat(response.statusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    assertThat(published).isEmpty();
  }

  @Test
  @DisplayName("Accepts a body of exactly 1 MiB without Content-Length")
  void acceptsBodyAtLimitWithoutContentLength() throws Exception {
    final byte[] body = new byte[SlackEventsEndpoint.MAX_BODY_BYTES];
    final byte[] json = "{\"type\":\"app_rate_limited\"}".getBytes(StandardCharsets.UTF_8);
    System.arraycopy(json, 0, body, 0, json.length);
    Arrays.fill(body, json.length, body.length, (byte) ' ');

    final ServerResponse response = endpoint.handle(request(body));

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
  }

  private static ServerRequest request(final byte[] body) {
    final MockHttpServletRequest servletRequest = new ChunkedRequest(body);
    servletRequest.addHeader(SlackEventsEndpoint.TIMESTAMP_HEADER, Long.toString(NOW));
    servletRequest.addHeader(
        SlackEventsEndpoint.SIGNATURE_HEADER, SlackRequestSigner.sign(SECRET, NOW, body));
    return ServerRequest.create(servletRequest, List.of());
  }

  /** A request whose length is unknown up front, as with {@code Transfer-Encoding: chunked}. */
  private static final class ChunkedRequest extends MockHttpServletRequest {

    ChunkedRequest(final byte[] body) {
      super("POST", SlackEventsProperties.DEFAULT_PATH);
      setContent(body);
    }

    @Override
    public int getContentLength() {
      return -1;
    }

    @Override
    public long getContentLengthLong() {
      return -1;
    }
  }
}
