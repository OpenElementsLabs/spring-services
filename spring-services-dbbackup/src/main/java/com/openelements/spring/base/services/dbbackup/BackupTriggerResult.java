package com.openelements.spring.base.services.dbbackup;

/**
 * Result of {@link DbBackupClient#triggerBackup()}.
 *
 * <p>The sidecar allows at most one backup at a time. When a backup is already running it answers a
 * trigger request with {@code 409 Conflict} and the still-running job's details. This wrapper makes
 * that distinction explicit: {@link #alreadyRunning()} is {@code true} when the returned {@link
 * #job()} refers to a pre-existing job rather than a freshly enqueued one.
 *
 * @param job the backup job returned by the sidecar, either freshly enqueued or already running
 * @param alreadyRunning {@code true} when {@code job} refers to a backup that was already in flight
 */
public record BackupTriggerResult(BackupJob job, boolean alreadyRunning) {}
