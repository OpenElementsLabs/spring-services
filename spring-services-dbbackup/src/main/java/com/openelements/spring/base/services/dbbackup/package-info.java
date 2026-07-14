/**
 * Client for the <a href="https://github.com/OpenElementsLabs/db-backup-service">db-backup-service
 * sidecar</a>.
 *
 * <p>The sidecar runs alongside the application and produces PostgreSQL dumps on a schedule. This
 * package provides a thin Java wrapper around its REST API so that a host application (e.g. a
 * frontend-facing controller) can trigger backups, poll job status, list artefacts and stream
 * downloads without re-implementing the HTTP wiring.
 *
 * <h2>Core types</h2>
 *
 * <ul>
 *   <li>{@link com.openelements.spring.base.services.dbbackup.DbBackupClient} — thin HTTP wrapper
 *       around the sidecar's REST API, built on Spring's {@code RestClient}.
 *   <li>{@link com.openelements.spring.base.services.dbbackup.BackupJob},
 *       {@link com.openelements.spring.base.services.dbbackup.BackupMetadata},
 *       {@link com.openelements.spring.base.services.dbbackup.BackupServiceInfo} — DTOs that mirror
 *       the sidecar's JSON payloads.
 *   <li>{@link com.openelements.spring.base.services.dbbackup.BackupTriggerResult} — wraps a
 *       {@link com.openelements.spring.base.services.dbbackup.BackupJob} together with an
 *       {@code alreadyRunning} flag that distinguishes the sidecar's {@code 202} (new job) from
 *       {@code 409} (a backup is already in flight) responses.
 *   <li>{@link com.openelements.spring.base.services.dbbackup.BackupDownload} — {@link
 *       java.lang.AutoCloseable} wrapper around the streamed gzip body returned by the download
 *       endpoints.
 * </ul>
 *
 * <h2>Configuration</h2>
 *
 * <p>Connection settings bind from the {@code openelements.db-backup} prefix via {@link
 * com.openelements.spring.base.services.dbbackup.DbBackupProperties}. The bearer token must be
 * supplied through environment variables or secret management — never committed to source control.
 */
package com.openelements.spring.base.services.dbbackup;
