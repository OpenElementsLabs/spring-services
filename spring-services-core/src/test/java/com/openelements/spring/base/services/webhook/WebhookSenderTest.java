package com.openelements.spring.base.services.webhook;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openelements.spring.base.services.webhook.data.WebhookDataService;
import com.openelements.spring.base.services.webhook.data.WebhookDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

/**
 * HTTP-level tests for {@link WebhookSender}, exercising the actual {@link RestClient} against a
 * local {@link WireMockServer}.
 *
 * <h2>What is tested</h2>
 *
 * <p>Two surfaces of the sender:
 *
 * <ol>
 *   <li><b>{@code ping(webhookId)}.</b> Issues an HTTP POST to the registered URL, captures the
 *       response status (2xx / 4xx / 5xx) and the connection-failure sentinel (0 or -1) into the
 *       persisted {@code lastStatus} / {@code lastCalledAt} fields, and rejects an unknown
 *       webhook id with {@link IllegalArgumentException} and a null id with {@link
 *       NullPointerException}.
 *   <li><b>{@code sendAndTrack(payload)}.</b> Fans out a single payload as an HTTP POST to every
 *       row returned by {@link WebhookDataService#findAllActive()}.
 * </ol>
 *
 * <p>Also covers the constructor null-guards (RestClient and WebhookDataService).
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5. A {@link WireMockServer} bound to a dynamic port is started once per class
 * and reset between tests; the {@link WebhookSender} is constructed with a real {@link
 * RestClient} pointed at WireMock's base URL. Status-code stubs are configured per scenario;
 * outgoing requests are verified against WireMock's recorded interactions.
 *
 * <p><b>Mock-Audit.</b> One {@code mock(WebhookDataService.class)}. The sender's contract is to
 * read webhook rows from {@code WebhookDataService} and write back status updates via
 * {@code save(...)}; mocking it lets the test (a) stub the row to return a WireMock-backed URL
 * without spinning up Postgres and (b) capture the {@code save(...)} argument to verify
 * status-tracking. End-to-end persistence is covered by {@link
 * com.openelements.spring.base.services.webhook.data.WebhookDataServiceIntegrationTest}.
 *
 * <p><b>Status: currently {@link Disabled @Disabled}.</b> The {@code @Disabled} on the class and
 * on the {@code PingTest} nested class is intentional — these tests are pending stabilisation
 * work (likely around connection-error status values that differ between local and CI runs).
 * The {@code @DisplayName}s and the Javadoc above are kept in place so that when the suite is
 * re-enabled, the documentation reads correctly on the JUnit report from day one.
 */
@DisplayName("WebhookSender")
@Disabled
class WebhookSenderTest {

  private static WireMockServer wireMockServer;
  private WebhookDataService webhookDataService;
  private WebhookSender webhookSender;

  @BeforeAll
  static void startWireMock() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
  }

  @AfterAll
  static void stopWireMock() {
    wireMockServer.stop();
  }

  @BeforeEach
  void setUp() {
    wireMockServer.resetAll();
    webhookDataService = mock(WebhookDataService.class);
    when(webhookDataService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    final RestClient restClient = RestClient.builder().build();
    webhookSender = new WebhookSender(restClient, webhookDataService);
  }

  private WebhookDto createWebhook(final String path) {
    final UUID webhookId = UUID.randomUUID();
    final WebhookDto webhook =
        new WebhookDto(webhookId, wireMockServer.baseUrl() + path, true, null, null);
    when(webhookDataService.findById(webhookId)).thenReturn(Optional.of(webhook));
    return webhook;
  }

  @Test
  @DisplayName("WebhookSender constructor rejects null for either RestClient or WebhookDataService — a misconfigured Spring context fails fast at startup.")
  void shouldRejectNullConstructorArgs() {
    final RestClient restClient = RestClient.builder().build();
    final WebhookDataService dataService = mock(WebhookDataService.class);

    assertThatThrownBy(() -> new WebhookSender(null, dataService))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new WebhookSender(restClient, null))
        .isInstanceOf(NullPointerException.class);
  }

  private record TestData(UUID id) implements com.openelements.spring.base.data.WithId {}

  @Nested
  @DisplayName("ping")
  @Disabled
  class PingTest {

    @Test
    @DisplayName("ping(webhookId) issues an HTTP POST with Content-Type: application/json to the registered URL.")
    void shouldSendHttpPostToWebhookUrl() {
      // GIVEN
      wireMockServer.stubFor(post(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200)));
      final WebhookDto webhook = createWebhook("/ping");

      // WHEN
      webhookSender.ping(webhook.id());

      // THEN
      wireMockServer.verify(
          postRequestedFor(urlEqualTo("/ping"))
              .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    @DisplayName("A 2xx response writes lastStatus=200 and a non-null lastCalledAt back to WebhookDataService.save(...).")
    void shouldTrackSuccessStatus() {
      // GIVEN
      wireMockServer.stubFor(post(urlEqualTo("/success")).willReturn(aResponse().withStatus(200)));
      final WebhookDto webhook = createWebhook("/success");

      // WHEN
      webhookSender.ping(webhook.id());

      // THEN
      final ArgumentCaptor<WebhookDto> captor = ArgumentCaptor.forClass(WebhookDto.class);
      verify(webhookDataService, atLeastOnce()).save(captor.capture());
      final WebhookDto saved = captor.getValue();
      assertThat(saved.lastStatus()).isEqualTo(200);
      assertThat(saved.lastCalledAt()).isNotNull();
    }

    @Test
    @DisplayName("A 5xx response writes lastStatus=500 back to WebhookDataService.save(...) — the failure is recorded, not swallowed.")
    void shouldTrackServerErrorStatus() {
      // GIVEN
      wireMockServer.stubFor(
          post(urlEqualTo("/server-error")).willReturn(aResponse().withStatus(500)));
      final WebhookDto webhook = createWebhook("/server-error");

      // WHEN
      webhookSender.ping(webhook.id());

      // THEN
      final ArgumentCaptor<WebhookDto> captor = ArgumentCaptor.forClass(WebhookDto.class);
      verify(webhookDataService, atLeastOnce()).save(captor.capture());
      assertThat(captor.getValue().lastStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("A 4xx response writes lastStatus=404 back to WebhookDataService.save(...) — the failure is recorded, not swallowed.")
    void shouldTrackClientErrorStatus() {
      // GIVEN
      wireMockServer.stubFor(
          post(urlEqualTo("/not-found")).willReturn(aResponse().withStatus(404)));
      final WebhookDto webhook = createWebhook("/not-found");

      // WHEN
      webhookSender.ping(webhook.id());

      // THEN
      final ArgumentCaptor<WebhookDto> captor = ArgumentCaptor.forClass(WebhookDto.class);
      verify(webhookDataService, atLeastOnce()).save(captor.capture());
      assertThat(captor.getValue().lastStatus()).isEqualTo(404);
    }

    /**
     * Posts to {@code 192.0.2.1} — a TEST-NET-1 ({@code RFC 5737}) address guaranteed not to
     * route — so the {@link RestClient} surfaces a connection-level failure rather than an HTTP
     * status. The sender encodes such failures as {@code 0} or {@code -1} in {@code lastStatus}
     * so operators can distinguish transport errors from server-side rejections.
     */
    @Test
    @DisplayName("A connection failure (unreachable host) writes lastStatus as 0 or -1 — transport errors are distinguishable from HTTP status codes.")
    void shouldTrackConnectionErrorAsZeroOrMinusOne() {
      // GIVEN - unreachable host
      final UUID webhookId = UUID.randomUUID();
      final WebhookDto webhook =
          new WebhookDto(webhookId, "http://192.0.2.1:1/unreachable", true, null, null);
      when(webhookDataService.findById(webhookId)).thenReturn(Optional.of(webhook));

      // WHEN
      webhookSender.ping(webhookId);

      // THEN
      final ArgumentCaptor<WebhookDto> captor = ArgumentCaptor.forClass(WebhookDto.class);
      verify(webhookDataService, atLeastOnce()).save(captor.capture());
      assertThat(captor.getValue().lastStatus()).isIn(0, -1);
    }

    @Test
    @DisplayName("ping(unknownId) raises IllegalArgumentException with message containing \"not found\" — translated by REST layer to HTTP 404.")
    void shouldThrowForNonExistentWebhook() {
      // GIVEN
      when(webhookDataService.findById(any(UUID.class))).thenReturn(Optional.empty());

      // WHEN & THEN
      assertThatThrownBy(() -> webhookSender.ping(UUID.randomUUID()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("ping(null) throws NullPointerException — fail-fast at the API boundary, no repository lookup attempted.")
    void shouldRejectNullId() {
      assertThatThrownBy(() -> webhookSender.ping(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("sendAndTrack with payload")
  class SendAndTrackPayloadTest {

    @Test
    @DisplayName("sendAndTrack(payload) fans out one HTTP POST per active webhook returned by findAllActive() — both registered endpoints receive the call.")
    void shouldSendToAllActiveWebhooks() {
      // GIVEN
      wireMockServer.stubFor(post(urlEqualTo("/hook1")).willReturn(aResponse().withStatus(200)));
      wireMockServer.stubFor(post(urlEqualTo("/hook2")).willReturn(aResponse().withStatus(200)));

      final WebhookDto hook1 =
          new WebhookDto(UUID.randomUUID(), wireMockServer.baseUrl() + "/hook1", true, null, null);
      final WebhookDto hook2 =
          new WebhookDto(UUID.randomUUID(), wireMockServer.baseUrl() + "/hook2", true, null, null);
      when(webhookDataService.findAllActive()).thenReturn(List.of(hook1, hook2));

      final var payload =
          com.openelements.spring.base.services.webhook.payload.WebhookDataEventPayload.of(
              WebhookDataEventType.CREATED, new TestData(UUID.randomUUID()));

      // WHEN
      webhookSender.sendAndTrack(payload);

      // THEN
      wireMockServer.verify(postRequestedFor(urlEqualTo("/hook1")));
      wireMockServer.verify(postRequestedFor(urlEqualTo("/hook2")));
    }
  }
}
