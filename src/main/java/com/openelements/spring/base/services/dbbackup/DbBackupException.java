package com.openelements.spring.base.services.dbbackup;

/** Thin runtime exception so callers can distinguish db-backup-service errors. */
public final class DbBackupException extends RuntimeException {

  public DbBackupException(final String message) {
    super(message);
  }

  public DbBackupException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
