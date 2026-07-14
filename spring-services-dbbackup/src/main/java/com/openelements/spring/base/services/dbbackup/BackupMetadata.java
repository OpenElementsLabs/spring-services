package com.openelements.spring.base.services.dbbackup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Metadata for a stored backup artefact.
 *
 * @param id the unique identifier of the backup artefact
 * @param createdAt the instant the backup was created
 * @param sizeBytes the size of the backup file in bytes
 * @param sha256 the SHA-256 checksum of the backup file
 * @param pgVersion the PostgreSQL server version the backup was taken from
 * @param durationMs how long the backup took to produce, in milliseconds
 * @param triggeredBy who or what initiated the backup (API call or scheduler)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BackupMetadata(
    String id,
    Instant createdAt,
    long sizeBytes,
    String sha256,
    String pgVersion,
    long durationMs,
    BackupTrigger triggeredBy) {}
