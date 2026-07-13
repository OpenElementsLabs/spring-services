package com.openelements.spring.base.services.dbbackup;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the <a
 * href="https://github.com/OpenElementsLabs/db-backup-service">db-backup-service</a> sidecar. Bound
 * from {@code openelements.db-backup.*} application properties.
 *
 * <p>The db-backup feature is <strong>opt-in</strong>: it is disabled by default and only activates
 * when {@code openelements.db-backup.enabled=true} (see {@link DbBackupConfig}). When the feature is
 * enabled, {@code baseUrl} is <strong>required</strong> — there is no default. A blank or missing
 * {@code baseUrl} fails context startup with a binding/validation error rather than silently
 * pointing at {@code localhost}, so a misconfiguration surfaces immediately instead of at first use.
 *
 * <p>The sidecar exposes two unauthenticated probes ({@code /health}, {@code /info}) and a set of
 * authenticated backup endpoints under {@code /api/v1/backups/*} that require a Bearer token
 * matching the sidecar's {@code API_TOKEN}. The token must be provided through environment
 * variables or secret management — never committed to source control.
 *
 * @param baseUrl the sidecar root URL (scheme + host + port, no trailing path). Required when the
 *     feature is enabled; no default.
 * @param apiToken the bearer token the sidecar accepts on its authenticated endpoints. May be
 *     {@code null} if only the unauthenticated probes are used; the client throws at the point of
 *     use when an authenticated call is made without a token.
 * @param requestTimeout per-request socket / read timeout. Defaults to 30 seconds because the
 *     download endpoints stream gzip blobs that can take a while.
 */
@Validated
@ConfigurationProperties("openelements.db-backup")
public record DbBackupProperties(
    @NotBlank String baseUrl, String apiToken, Duration requestTimeout) {

  public DbBackupProperties {
    if (requestTimeout == null) {
      requestTimeout = Duration.ofSeconds(30);
    }
  }
}
