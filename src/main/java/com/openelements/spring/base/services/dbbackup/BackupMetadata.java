package com.openelements.spring.base.services.dbbackup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** Metadata for a stored backup artefact. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BackupMetadata(
    String id,
    Instant createdAt,
    long sizeBytes,
    String sha256,
    String pgVersion,
    long durationMs,
    BackupTrigger triggeredBy) {}
