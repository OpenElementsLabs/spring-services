package com.openelements.spring.base.services.dbbackup;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the <a
 * href="https://github.com/OpenElementsLabs/db-backup-service">db-backup-service</a> sidecar. Bound
 * from {@code openelements.db-backup.*} application properties.
 *
 * <p>The sidecar exposes two unauthenticated probes ({@code /health}, {@code /info}) and a set of
 * authenticated backup endpoints under {@code /api/v1/backups/*} that require a Bearer token
 * matching the sidecar's {@code API_TOKEN}. The token must be provided through environment
 * variables or secret management — never committed to source control.
 *
 * @param baseUrl the sidecar root URL (scheme + host + port, no trailing path). Defaults to
 *     {@code http://localhost:8081}.
 * @param apiToken the bearer token the sidecar accepts on its authenticated endpoints. May be
 *     {@code null} if only the unauthenticated probes are used; the client throws at the point of
 *     use when an authenticated call is made without a token.
 * @param requestTimeout per-request socket / read timeout. Defaults to 30 seconds because the
 *     download endpoints stream gzip blobs that can take a while.
 */
@ConfigurationProperties("openelements.db-backup")
public record DbBackupProperties(String baseUrl, String apiToken, Duration requestTimeout) {

  public DbBackupProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "http://localhost:8081";
    }
    if (requestTimeout == null) {
      requestTimeout = Duration.ofSeconds(30);
    }
  }
}
