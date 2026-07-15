package com.openelements.spring.base.services.dbbackup;

/** Thin runtime exception so callers can distinguish db-backup-service errors. */
public final class DbBackupException extends RuntimeException {

  /**
   * Creates a new exception with the given detail message.
   *
   * @param message the detail message describing the db-backup-service error
   */
  public DbBackupException(final String message) {
    super(message);
  }

  /**
   * Creates a new exception with the given detail message and cause.
   *
   * @param message the detail message describing the db-backup-service error
   * @param cause the underlying cause of this error
   */
  public DbBackupException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
