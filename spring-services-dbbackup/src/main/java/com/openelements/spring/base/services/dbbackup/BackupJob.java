package com.openelements.spring.base.services.dbbackup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Snapshot of a backup job. Fields are {@code null} until the corresponding lifecycle event has
 * occurred — e.g. {@code startedAt}, {@code finishedAt}, {@code durationMs} and {@code backupId}
 * remain {@code null} while the job is {@link BackupJobStatus#QUEUED queued}.
 *
 * @param jobId the sidecar-assigned identifier of the backup job
 * @param status the current lifecycle state of the job
 * @param triggeredBy who or what initiated the job (API call or scheduler)
 * @param startedAt the instant the job started running, or {@code null} while still queued
 * @param finishedAt the instant the job finished, or {@code null} while not yet finished
 * @param durationMs how long the job ran in milliseconds, or {@code null} while not yet finished
 * @param errorMessage the failure message when the job failed, or {@code null} otherwise
 * @param backupId the identifier of the produced backup artefact, or {@code null} until one exists
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
