package com.openelements.spring.base.services.webhook;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.openelements.spring.base.services.webhook.data.WebhookDataService;
import com.openelements.spring.base.services.webhook.data.WebhookDto;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link WebhookSender} using WireMock to verify actual HTTP calls.
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
    void shouldRejectNullConstructorArgs() {
        final RestClient restClient = RestClient.builder().build();
        final WebhookDataService dataService = mock(WebhookDataService.class);

        assertThatThrownBy(() -> new WebhookSender(null, dataService))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WebhookSender(restClient, null))
                .isInstanceOf(NullPointerException.class);
    }

    private record TestData(UUID id) implements com.openelements.spring.base.data.WithId {
    }

    @Nested
    @DisplayName("ping")
    @Disabled
    class PingTest {

        @Test
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

        @Test
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
        void shouldThrowForNonExistentWebhook() {
            // GIVEN
            when(webhookDataService.findById(any(UUID.class))).thenReturn(Optional.empty());

            // WHEN & THEN
            assertThatThrownBy(() -> webhookSender.ping(UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        void shouldRejectNullId() {
            assertThatThrownBy(() -> webhookSender.ping(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("sendAndTrack with payload")
    class SendAndTrackPayloadTest {

        @Test
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
