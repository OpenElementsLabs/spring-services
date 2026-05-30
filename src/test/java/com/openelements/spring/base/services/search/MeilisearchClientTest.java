package com.openelements.spring.base.services.search;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Wire-level tests for {@link MeilisearchClient}. Uses WireMock to verify request paths, the JSON
 * shapes we send, and the Authorization header.
 */
class MeilisearchClientTest {

  private static WireMockServer wireMockServer;
  private MeilisearchClient client;

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
    final MeilisearchProperties props =
        new MeilisearchProperties(wireMockServer.baseUrl(), "master-key", "crm_", null);
    client = new MeilisearchClient(props, new ObjectMapper());
  }

  @Test
  void isHealthyReturnsTrueOnStatusAvailable() {
    wireMockServer.stubFor(
        get(urlEqualTo("/health"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"available\"}")));

    assertThat(client.isHealthy()).isTrue();

    wireMockServer.verify(getRequestedFor(urlEqualTo("/health")));
  }

  @Test
  void isHealthyReturnsFalseOnServerError() {
    wireMockServer.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(500)));

    assertThat(client.isHealthy()).isFalse();
  }

  @Test
  void addDocumentsSendsBearerAndReturnsTaskUid() {
    wireMockServer.stubFor(
        post(urlEqualTo("/indexes/crm_companies/documents"))
            .willReturn(
                aResponse()
                    .withStatus(202)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"taskUid\":42}")));

    final long taskUid =
        client.addDocuments("crm_companies", List.of(Map.of("id", "abc", "name", "Acme")));

    assertThat(taskUid).isEqualTo(42L);
    wireMockServer.verify(
        postRequestedFor(urlEqualTo("/indexes/crm_companies/documents"))
            .withHeader("Authorization", equalTo("Bearer master-key"))
            .withRequestBody(containing("\"name\":\"Acme\"")));
  }

  @Test
  void useApiKeySwitchesAuthorizationHeader() {
    client.useApiKey("scoped-key");
    wireMockServer.stubFor(
        get(urlEqualTo("/health"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"available\"}")));

    client.isHealthy();

    wireMockServer.verify(
        getRequestedFor(urlEqualTo("/health"))
            .withHeader("Authorization", equalTo("Bearer scoped-key")));
  }

  @Test
  void deleteDocumentSendsDeleteWithPath() {
    wireMockServer.stubFor(
        delete(urlEqualTo("/indexes/crm_contacts/documents/abc-123"))
            .willReturn(
                aResponse()
                    .withStatus(202)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"taskUid\":7}")));

    client.deleteDocument("crm_contacts", "abc-123");

    wireMockServer.verify(
        deleteRequestedFor(urlEqualTo("/indexes/crm_contacts/documents/abc-123")));
  }

  @Test
  void multiSearchPostsToMultiSearch() {
    wireMockServer.stubFor(
        post(urlEqualTo("/multi-search"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"results\":[]}")));

    client.multiSearch(Map.of("queries", List.of()));

    wireMockServer.verify(postRequestedFor(urlEqualTo("/multi-search")));
  }

  @Test
  void createScopedKeyParsesKeyField() {
    wireMockServer.stubFor(
        post(urlEqualTo("/keys"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"key\":\"derived-key-xyz\"}")));

    final String key = client.createScopedKey(List.of("crm_*"), List.of("search"));

    assertThat(key).isEqualTo("derived-key-xyz");
    wireMockServer.verify(postRequestedFor(urlEqualTo("/keys")));
  }

  @Test
  void waitForTaskReturnsSucceededWhenStatusReached() {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/tasks/1"))
            .inScenario("task-polling")
            .whenScenarioStateIs(STARTED)
            .willSetStateTo("succeeded")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"enqueued\"}")));
    wireMockServer.stubFor(
        get(urlPathEqualTo("/tasks/1"))
            .inScenario("task-polling")
            .whenScenarioStateIs("succeeded")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"succeeded\"}")));

    assertThat(client.waitForTask(1L, Duration.ofSeconds(2))).isEqualTo(TaskOutcome.SUCCEEDED);
  }

  @Test
  void waitForTaskReturnsTimedOutWhenStatusNeverReached() {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/tasks/1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"enqueued\"}")));

    assertThat(client.waitForTask(1L, Duration.ofMillis(100))).isEqualTo(TaskOutcome.TIMED_OUT);
  }
}
