package com.openelements.spring.base.services.email;

/** Unchecked exception thrown by {@link EmailService} for any email-related failure. */
public class EmailException extends RuntimeException {

  /**
   * Creates a new exception with the given detail message.
   *
   * @param message the detail message describing the email failure
   */
  public EmailException(final String message) {
    super(message);
  }

  /**
   * Creates a new exception with the given detail message and cause.
   *
   * @param message the detail message describing the email failure
   * @param cause the underlying cause of the failure
   */
  public EmailException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
