package com.openelements.spring.base.services.dbbackup;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;

/**
 * Thin HTTP wrapper around the <a
 * href="https://github.com/OpenElementsLabs/db-backup-service">db-backup-service</a> sidecar REST
 * API. Uses Spring's {@link RestClient} (already on the classpath via {@code spring-web}).
 *
 * <p>The sidecar exposes two unauthenticated probes ({@code /health}, {@code /info}) and a set of
 * authenticated endpoints under {@code /api/v1/backups/*}. Authenticated calls send the configured
 * {@code openelements.db-backup.api-token} as a Bearer token; if the property is unset, the client
 * throws a {@link DbBackupException} at the point of use rather than silently issuing an
 * unauthenticated request.
 */
@Component
public class DbBackupClient {

  private final RestClient restClient;
  private final DbBackupProperties props;

  /**
   * Creates a client bound to the sidecar described by the given properties.
   *
   * @param props the db-backup-service connection settings; must not be {@code null}
   */
  public DbBackupClient(final DbBackupProperties props) {
    this.props = Objects.requireNonNull(props, "props must not be null");
    this.restClient =
        RestClient.builder()
            .baseUrl(props.baseUrl())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  /**
   * Returns {@code true} when the sidecar's {@code /health} probe answers {@code 2xx}.
   *
   * @return {@code true} if the sidecar reports healthy, {@code false} otherwise
   */
  public boolean isHealthy() {
    try {
      return restClient
          .get()
          .uri("/health")
          .exchange((req, res) -> res.getStatusCode().is2xxSuccessful());
    } catch (final RestClientException e) {
      return false;
    }
  }

  /**
   * Reads the sidecar's {@code /info} metadata. Unauthenticated.
   *
   * @return the service metadata reported by the sidecar
   */
  public BackupServiceInfo getInfo() {
    return restClient
        .get()
        .uri("/info")
        .exchange(
            (req, res) -> {
              if (!res.getStatusCode().is2xxSuccessful()) {
                throw new DbBackupException(
                    "GET /info returned HTTP " + res.getStatusCode().value());
              }
              return res.bodyTo(BackupServiceInfo.class);
            });
  }

  /**
   * Triggers a new backup via {@code POST /api/v1/backups}. The sidecar allows at most one backup
   * to run at a time; if a backup is already in flight the returned
   * {@link BackupTriggerResult#alreadyRunning()} is {@code true} and {@link
   * BackupTriggerResult#job()} refers to the still-running job.
   *
   * @return the result describing the enqueued or already-running backup job
   */
  public BackupTriggerResult triggerBackup() {
    return restClient
        .post()
        .uri("/api/v1/backups")
        .header(HttpHeaders.AUTHORIZATION, bearerAuth())
        .exchange(
            (req, res) -> {
              final int status = res.getStatusCode().value();
              return switch (status) {
                case 202 -> new BackupTriggerResult(res.bodyTo(BackupJob.class), false);
                case 409 -> new BackupTriggerResult(res.bodyTo(BackupJob.class), true);
                case 401 ->
                    throw new DbBackupException("Sidecar rejected the API token (HTTP 401)");
                default ->
                    throw new DbBackupException("POST /api/v1/backups failed: HTTP " + status);
              };
            });
  }

  /**
   * Returns the current state of a backup job, or empty if the sidecar doesn't know it (404).
   *
   * @param jobId the identifier of the backup job to look up
   * @return the job snapshot, or empty if no job with that id exists
   */
  public Optional<BackupJob> getJob(final String jobId) {
    Objects.requireNonNull(jobId, "jobId must not be null");
    return restClient
        .get()
        .uri("/api/v1/backups/jobs/{jobId}", jobId)
        .header(HttpHeaders.AUTHORIZATION, bearerAuth())
        .exchange(
            (req, res) -> {
              final int status = res.getStatusCode().value();
              return switch (status) {
                case 200 -> Optional.of(res.bodyTo(BackupJob.class));
                case 404 -> Optional.<BackupJob>empty();
                case 401 ->
                    throw new DbBackupException("Sidecar rejected the API token (HTTP 401)");
                default ->
                    throw new DbBackupException(
                        "GET /api/v1/backups/jobs/" + jobId + " failed: HTTP " + status);
              };
            });
  }

  /**
   * Lists every available backup, newest first.
   *
   * @return the metadata of all available backups, ordered newest first
   */
  public List<BackupMetadata> listBackups() {
    return restClient
        .get()
        .uri("/api/v1/backups")
        .header(HttpHeaders.AUTHORIZATION, bearerAuth())
        .exchange(
            (req, res) -> {
              final int status = res.getStatusCode().value();
              if (status == 401) {
                throw new DbBackupException("Sidecar rejected the API token (HTTP 401)");
              }
              if (!res.getStatusCode().is2xxSuccessful()) {
                throw new DbBackupException("GET /api/v1/backups failed: HTTP " + status);
              }
              return res.bodyTo(new ParameterizedTypeReference<List<BackupMetadata>>() {});
            });
  }

  /**
   * Returns metadata for the newest successful backup, or empty if there isn't one yet (404).
   *
   * @return the metadata of the latest backup, or empty if none exists yet
   */
  public Optional<BackupMetadata> getLatestBackup() {
    return restClient
        .get()
        .uri("/api/v1/backups/latest")
        .header(HttpHeaders.AUTHORIZATION, bearerAuth())
        .exchange(
            (req, res) -> {
              final int status = res.getStatusCode().value();
              return switch (status) {
                case 200 -> Optional.of(res.bodyTo(BackupMetadata.class));
                case 404 -> Optional.<BackupMetadata>empty();
                case 401 ->
                    throw new DbBackupException("Sidecar rejected the API token (HTTP 401)");
                default ->
                    throw new DbBackupException(
                        "GET /api/v1/backups/latest failed: HTTP " + status);
              };
            });
  }

  /**
   * Streams the gzip body of a specific backup. The returned {@link BackupDownload} owns the HTTP
   * connection — always consume it in a try-with-resources.
   *
   * @param id the identifier of the backup to download
   * @return a handle over the open download stream
   */
  public BackupDownload downloadBackup(final String id) {
    Objects.requireNonNull(id, "id must not be null");
    return restClient
        .get()
        .uri("/api/v1/backups/{id}/download", id)
        .header(HttpHeaders.AUTHORIZATION, bearerAuth())
        .exchange((req, res) -> openDownload(id, res), false);
  }

  /**
   * Streams the gzip body of the newest successful backup. See {@link #downloadBackup(String)}.
   *
   * @return a handle over the open download stream for the latest backup
   */
  public BackupDownload downloadLatestBackup() {
    return restClient
        .get()
        .uri("/api/v1/backups/latest/download")
        .header(HttpHeaders.AUTHORIZATION, bearerAuth())
        .exchange((req, res) -> openDownload("latest", res), false);
  }

  private BackupDownload openDownload(final String id, final ConvertibleClientHttpResponse res)
      throws IOException {
    final int status = res.getStatusCode().value();
    if (status == 200) {
      return new BackupDownload(id, res.getHeaders().getContentLength(), res.getBody(), res);
    }
    closeQuietly(res);
    throw switch (status) {
      case 404 -> new DbBackupException("Backup not found: " + id);
      case 401 -> new DbBackupException("Sidecar rejected the API token (HTTP 401)");
      default -> new DbBackupException("Download of " + id + " failed: HTTP " + status);
    };
  }

  private static void closeQuietly(final ClientHttpResponse res) {
    try {
      res.close();
    } catch (final RuntimeException ignored) {
      // best-effort: we're already on the error path
    }
  }

  private String bearerAuth() {
    final String token = props.apiToken();
    if (token == null || token.isBlank()) {
      throw new DbBackupException("openelements.db-backup.api-token is not configured");
    }
    return "Bearer " + token;
  }
}
