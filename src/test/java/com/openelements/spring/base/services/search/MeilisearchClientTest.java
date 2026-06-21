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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wire-level tests for {@link MeilisearchClient}.
 *
 * <h2>What is tested</h2>
 *
 * <p>The HTTP contract this client speaks with Meilisearch: request methods and paths
 * ({@code GET /health}, {@code POST /indexes/{uid}/documents}, {@code DELETE
 * /indexes/{uid}/documents/{id}}, {@code POST /multi-search}, {@code POST /keys},
 * {@code GET /tasks/{id}}); the JSON shapes we send and the {@code Authorization: Bearer ...}
 * header (master key vs. scoped key via {@link MeilisearchClient#useApiKey(String)}); how
 * responses are parsed ({@code taskUid} field, {@code key} field on scoped-key creation,
 * {@code status} polling); how {@link MeilisearchClient#waitForTask(long, Duration)} returns
 * {@link TaskOutcome#SUCCEEDED} once the task reaches a terminal status and {@link
 * TaskOutcome#TIMED_OUT} when polling exceeds the budget; and how {@link
 * MeilisearchClient#isHealthy()} returns {@code false} on non-2xx without throwing.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Pure JUnit 5 with a per-suite WireMock server on a dynamic port acting as a fake
 * Meilisearch. A real {@link MeilisearchClient} backed by a real {@link ObjectMapper} talks to
 * the WireMock baseUrl over real HTTP — only the server peer is faked. The
 * {@code waitForTask} timing-out test uses a 100 ms budget against a stub that always returns
 * {@code enqueued}, so the timeout outcome is exercised without sleeping the test for the full
 * production duration.
 *
 * <p><b>Mock-Audit.</b> Zero Mockito mocks. WireMock is used as a fake HTTP peer — that is the
 * correct test double for a thin HTTP client like this one, because mocking {@link
 * java.net.http.HttpClient} would only re-encode what the wire-level WireMock test already
 * checks (the actual bytes on the wire). The client under test is a real instance, not a spy.
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
  @DisplayName("isHealthy() returns true when GET /health responds 200 with status=available.")
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
  @DisplayName("isHealthy() returns false (does not throw) when GET /health responds 500.")
  void isHealthyReturnsFalseOnServerError() {
    wireMockServer.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(500)));

    assertThat(client.isHealthy()).isFalse();
  }

  /**
   * Pins three wire-level details simultaneously: the POST path includes the index uid, the
   * request carries {@code Authorization: Bearer master-key}, and the request body contains the
   * serialised document. The returned {@code taskUid} from the response body is surfaced to the
   * caller as a {@code long}.
   */
  @Test
  @DisplayName(
      "addDocuments(uid, docs) POSTs to /indexes/{uid}/documents with Bearer auth and returns the taskUid from the response.")
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

  /**
   * Confirms the runtime key downgrade: after {@code useApiKey("scoped-key")} the Authorization
   * header on subsequent requests carries the scoped key, not the master key that was passed to
   * the constructor.
   */
  @Test
  @DisplayName(
      "useApiKey(...) switches subsequent requests from Bearer master-key to Bearer scoped-key.")
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
  @DisplayName("deleteDocument(uid, id) issues HTTP DELETE on /indexes/{uid}/documents/{id}.")
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
  @DisplayName("multiSearch(payload) POSTs to /multi-search.")
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
  @DisplayName(
      "createScopedKey(...) POSTs to /keys and returns the derived key extracted from the response's key field.")
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

  /**
   * Two-step WireMock scenario: first poll returns {@code enqueued}, second poll returns
   * {@code succeeded}. Verifies that the client keeps polling until a terminal status arrives
   * within the budget and returns {@link TaskOutcome#SUCCEEDED}.
   */
  @Test
  @DisplayName(
      "waitForTask(id, budget) polls /tasks/{id} until status becomes succeeded and returns SUCCEEDED.")
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
  @DisplayName(
      "waitForTask(id, 100ms) returns TIMED_OUT when the task never leaves the enqueued status within budget.")
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
