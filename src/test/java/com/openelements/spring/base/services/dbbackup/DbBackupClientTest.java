package com.openelements.spring.base.services.dbbackup;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wire-level tests for {@link DbBackupClient}. Uses WireMock to verify request paths, the
 * Authorization header on authenticated calls, and that the JSON shapes we receive deserialise
 * into the documented DTOs.
 *
 * <h2>What is tested</h2>
 *
 * <p>The HTTP contract between {@link DbBackupClient} and its remote backup service partner:
 *
 * <ul>
 *   <li>{@code isHealthy()} maps HTTP {@code 200} to {@code true} and HTTP {@code 503} to
 *       {@code false} — the health probe does not throw.
 *   <li>{@code getInfo()} deserialises the nested service-info JSON (version, pgDump version,
 *       retention, backup interval, last-backup age) into {@link BackupServiceInfo}.
 *   <li>{@code triggerBackup()} accepts HTTP {@code 202} as a successful enqueue, treats HTTP
 *       {@code 409} as an "already running" signal (returning the existing job rather than
 *       throwing), and propagates {@code 401} as a {@link DbBackupException}. Missing
 *       configuration ({@code api-token} unset) fails fast before any network call.
 *   <li>{@code getJob(id)} parses a fully populated terminal-state job; HTTP {@code 404}
 *       returns {@code Optional.empty()} — not an error.
 *   <li>{@code listBackups()} parses the array shape and maps {@code triggeredBy} to the
 *       {@link BackupTrigger} enum.
 *   <li>{@code getLatestBackup()} returns {@code Optional.empty()} on {@code 404} — a brand-new
 *       installation with no backups yet is a valid state.
 *   <li>{@code downloadBackup(id)} streams the response body as an auto-closeable {@link
 *       BackupDownload}, reads {@code Content-Length} into {@code sizeBytes}, and sends the
 *       bearer token. A {@code 404} on download surfaces as {@link DbBackupException} (unlike
 *       {@code getLatestBackup}, downloading a missing id is a programming error).
 * </ul>
 *
 * <p>The Authorization header is asserted on the authenticated routes ({@code triggerBackup},
 * {@code downloadBackup}) — silently dropping the token would break production immediately, so
 * the test pins it explicitly.
 *
 * <h2>How it is tested</h2>
 *
 * <p>WireMock runs as a JVM-local HTTP server on a dynamic port; the client is constructed with
 * its {@code baseUrl} pointed at WireMock. Each test stubs the routes it needs and the client
 * is exercised end-to-end through its real JSON deserialiser. Pure JUnit 5 — no Spring context.
 *
 * <p><b>Mock-Audit.</b> Zero Mockito mocks. WireMock is a network-level fake (not a mock of the
 * {@code DbBackupClient} collaborator) — it replaces the remote service, not the client logic
 * under test. The {@link DbBackupClient}, the JSON deserialiser, and the {@link
 * DbBackupProperties} configuration are all real.
 */
class DbBackupClientTest {

  private static WireMockServer wireMockServer;
  private DbBackupClient client;

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
    final DbBackupProperties props =
        new DbBackupProperties(wireMockServer.baseUrl(), "secret-token", null);
    client = new DbBackupClient(props);
  }

  @Test
  @DisplayName("isHealthy() returns true when the /health endpoint responds with HTTP 200.")
  void isHealthyReturnsTrueOn200() {
    wireMockServer.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));

    assertThat(client.isHealthy()).isTrue();

    wireMockServer.verify(getRequestedFor(urlEqualTo("/health")));
  }

  @Test
  @DisplayName("isHealthy() returns false (not throws) when /health responds with HTTP 503.")
  void isHealthyReturnsFalseOn503() {
    wireMockServer.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(503)));

    assertThat(client.isHealthy()).isFalse();
  }

  @Test
  @DisplayName(
      "getInfo() deserialises the nested service-info JSON (version, retention, backup interval, last-backup age).")
  void getInfoParsesNestedShape() {
    final String body =
        """
        {
          "version": "1.2.3",
          "pgDumpVersion": "17.2",
          "retention": { "days": 7 },
          "backupInterval": { "iso8601": "PT24H", "seconds": 86400 },
          "backup": { "lastSuccessfulBackupAgeSeconds": 3600 }
        }
        """;
    wireMockServer.stubFor(
        get(urlEqualTo("/info"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));

    final BackupServiceInfo info = client.getInfo();

    assertThat(info.version()).isEqualTo("1.2.3");
    assertThat(info.pgDumpVersion()).isEqualTo("17.2");
    assertThat(info.retention().days()).isEqualTo(7);
    assertThat(info.backupInterval().iso8601()).isEqualTo("PT24H");
    assertThat(info.backupInterval().seconds()).isEqualTo(86400L);
    assertThat(info.backup().lastSuccessfulBackupAgeSeconds()).isEqualTo(3600L);
  }

  /**
   * Verifies two things at once: HTTP {@code 202} maps to {@code alreadyRunning=false} with the
   * parsed queued {@link BackupJob}, and the request carried the configured bearer token —
   * silently dropping the Authorization header would break production immediately.
   */
  @Test
  @DisplayName(
      "triggerBackup() accepts HTTP 202 as a fresh enqueue and sends the Bearer token on the request.")
  void triggerBackupReturnsFreshJobOn202() {
    final String body =
        """
        {
          "jobId": "9b7c4a8e",
          "status": "queued",
          "triggeredBy": "api",
          "startedAt": null,
          "finishedAt": null,
          "durationMs": null,
          "errorMessage": null,
          "backupId": null
        }
        """;
    wireMockServer.stubFor(
        post(urlEqualTo("/api/v1/backups"))
            .willReturn(
                aResponse()
                    .withStatus(202)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));

    final BackupTriggerResult result = client.triggerBackup();

    assertThat(result.alreadyRunning()).isFalse();
    assertThat(result.job().jobId()).isEqualTo("9b7c4a8e");
    assertThat(result.job().status()).isEqualTo(BackupJobStatus.QUEUED);
    assertThat(result.job().triggeredBy()).isEqualTo(BackupTrigger.API);

    wireMockServer.verify(
        postRequestedFor(urlEqualTo("/api/v1/backups"))
            .withHeader("Authorization", equalTo("Bearer secret-token")));
  }

  @Test
  @DisplayName(
      "triggerBackup() maps HTTP 409 to alreadyRunning=true and returns the in-progress job — not an error.")
  void triggerBackupSignalsAlreadyRunningOn409() {
    final String body =
        """
        {
          "jobId": "running-id",
          "status": "running",
          "triggeredBy": "scheduler",
          "startedAt": "2026-05-10T01:00:00Z",
          "finishedAt": null,
          "durationMs": null,
          "errorMessage": null,
          "backupId": null
        }
        """;
    wireMockServer.stubFor(
        post(urlEqualTo("/api/v1/backups"))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));

    final BackupTriggerResult result = client.triggerBackup();

    assertThat(result.alreadyRunning()).isTrue();
    assertThat(result.job().jobId()).isEqualTo("running-id");
    assertThat(result.job().status()).isEqualTo(BackupJobStatus.RUNNING);
    assertThat(result.job().triggeredBy()).isEqualTo(BackupTrigger.SCHEDULER);
  }

  @Test
  @DisplayName(
      "triggerBackup() propagates HTTP 401 as DbBackupException with the status code in the message.")
  void triggerBackupThrowsOnUnauthorized() {
    wireMockServer.stubFor(
        post(urlEqualTo("/api/v1/backups")).willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(client::triggerBackup)
        .isInstanceOf(DbBackupException.class)
        .hasMessageContaining("401");
  }

  @Test
  @DisplayName(
      "triggerBackup() with no configured api-token fails fast before any network call is made.")
  void triggerBackupThrowsWhenApiTokenMissing() {
    final DbBackupClient anonymous =
        new DbBackupClient(new DbBackupProperties(wireMockServer.baseUrl(), null, null));

    assertThatThrownBy(anonymous::triggerBackup)
        .isInstanceOf(DbBackupException.class)
        .hasMessageContaining("api-token");
  }

  @Test
  @DisplayName(
      "getJob(id) on HTTP 200 returns a fully populated BackupJob — status, duration, and backupId all parsed.")
  void getJobReturnsPresentOn200() {
    final String body =
        """
        {
          "jobId": "abc",
          "status": "succeeded",
          "triggeredBy": "scheduler",
          "startedAt": "2026-05-10T01:00:00Z",
          "finishedAt": "2026-05-10T01:01:14Z",
          "durationMs": 74123,
          "errorMessage": null,
          "backupId": "backup_20260510T010000Z.sql.gz"
        }
        """;
    wireMockServer.stubFor(
        get(urlEqualTo("/api/v1/backups/jobs/abc"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));

    final Optional<BackupJob> job = client.getJob("abc");

    assertThat(job).isPresent();
    assertThat(job.get().status()).isEqualTo(BackupJobStatus.SUCCEEDED);
    assertThat(job.get().durationMs()).isEqualTo(74123L);
    assertThat(job.get().backupId()).isEqualTo("backup_20260510T010000Z.sql.gz");
  }

  @Test
  @DisplayName("getJob(unknownId) on HTTP 404 returns Optional.empty() — a missing job is not an error.")
  void getJobReturnsEmptyOn404() {
    wireMockServer.stubFor(
        get(urlEqualTo("/api/v1/backups/jobs/nope")).willReturn(aResponse().withStatus(404)));

    assertThat(client.getJob("nope")).isEmpty();
  }

  @Test
  @DisplayName("listBackups() parses the JSON array response and maps triggeredBy to the BackupTrigger enum.")
  void listBackupsParsesArray() {
    final String body =
        """
        [
          {
            "id": "backup_20260510T010000Z.sql.gz",
            "createdAt": "2026-05-10T01:01:14Z",
            "sizeBytes": 8421376,
            "sha256": "fa3c",
            "pgVersion": "17.2",
            "durationMs": 74123,
            "triggeredBy": "scheduler"
          }
        ]
        """;
    wireMockServer.stubFor(
        get(urlEqualTo("/api/v1/backups"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));

    final List<BackupMetadata> backups = client.listBackups();

    assertThat(backups).hasSize(1);
    assertThat(backups.getFirst().id()).isEqualTo("backup_20260510T010000Z.sql.gz");
    assertThat(backups.getFirst().sizeBytes()).isEqualTo(8421376L);
    assertThat(backups.getFirst().triggeredBy()).isEqualTo(BackupTrigger.SCHEDULER);
  }

  @Test
  @DisplayName(
      "getLatestBackup() on HTTP 404 returns Optional.empty() — a brand-new install with no backups is valid.")
  void getLatestBackupReturnsEmptyOn404() {
    wireMockServer.stubFor(
        get(urlEqualTo("/api/v1/backups/latest")).willReturn(aResponse().withStatus(404)));

    assertThat(client.getLatestBackup()).isEmpty();
  }

  @Test
  @DisplayName(
      "downloadBackup(id) streams the body, reports the Content-Length, and sends the Bearer token.")
  void downloadBackupStreamsBody() throws Exception {
    final byte[] payload = "fake-gzip-bytes".getBytes(StandardCharsets.UTF_8);
    wireMockServer.stubFor(
        get(urlEqualTo("/api/v1/backups/abc/download"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/gzip")
                    .withHeader("Content-Length", Integer.toString(payload.length))
                    .withBody(payload)));

    try (BackupDownload dl = client.downloadBackup("abc")) {
      assertThat(dl.id()).isEqualTo("abc");
      assertThat(dl.sizeBytes()).isEqualTo(payload.length);
      assertThat(dl.stream().readAllBytes()).isEqualTo(payload);
    }

    wireMockServer.verify(
        getRequestedFor(urlEqualTo("/api/v1/backups/abc/download"))
            .withHeader("Authorization", equalTo("Bearer secret-token")));
  }

  @Test
  @DisplayName(
      "downloadBackup(missingId) throws DbBackupException on HTTP 404 — unlike getLatestBackup, a missing download id is a programming error.")
  void downloadBackupThrowsOn404() {
    wireMockServer.stubFor(
        get(urlEqualTo("/api/v1/backups/missing/download"))
            .willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(() -> client.downloadBackup("missing"))
        .isInstanceOf(DbBackupException.class)
        .hasMessageContaining("missing");
  }
}
