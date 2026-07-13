package com.openelements.spring.base.services.dbbackup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Snapshot of a backup job. Fields are {@code null} until the corresponding lifecycle event has
 * occurred — e.g. {@code startedAt}, {@code finishedAt}, {@code durationMs} and {@code backupId}
 * remain {@code null} while the job is {@link BackupJobStatus#QUEUED queued}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BackupJob(
    String jobId,
    BackupJobStatus status,
    BackupTrigger triggeredBy,
    @Nullable Instant startedAt,
    @Nullable Instant finishedAt,
    @Nullable Long durationMs,
    @Nullable String errorMessage,
    @Nullable String backupId) {}
