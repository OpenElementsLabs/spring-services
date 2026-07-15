package com.openelements.spring.base.services.dbbackup;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Streamed gzip body of a backup download. The underlying HTTP connection is held open until {@link
 * #close()} is called — always consume this in a try-with-resources.
 *
 * <pre>{@code
 * try (BackupDownload dl = client.downloadLatestBackup()) {
 *   Files.copy(dl.stream(), Path.of("dump.sql.gz"));
 * }
 * }</pre>
 */
public final class BackupDownload implements AutoCloseable {

  private final String id;
  private final long sizeBytes;
  private final InputStream stream;
  private final Closeable httpResponse;

  BackupDownload(
      final String id,
      final long sizeBytes,
      final InputStream stream,
      final Closeable httpResponse) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.sizeBytes = sizeBytes;
    this.stream = Objects.requireNonNull(stream, "stream must not be null");
    this.httpResponse = Objects.requireNonNull(httpResponse, "httpResponse must not be null");
  }

  /**
   * Identifier of the backup being downloaded (e.g. {@code backup_20260510T010000Z.sql.gz}).
   *
   * @return the backup identifier
   */
  public String id() {
    return id;
  }

  /**
   * Content length advertised by the sidecar, or {@code -1} if not known.
   *
   * @return the size of the download in bytes, or {@code -1} if unknown
   */
  public long sizeBytes() {
    return sizeBytes;
  }

  /**
   * Gzipped SQL dump as a stream. Caller is responsible for closing via {@link #close()}.
   *
   * @return the gzipped SQL dump stream
   */
  public InputStream stream() {
    return stream;
  }

  @Override
  public void close() throws IOException {
    try (stream;
        httpResponse) {
      // try-with-resources closes both, surfacing the first IOException.
    }
  }
}
