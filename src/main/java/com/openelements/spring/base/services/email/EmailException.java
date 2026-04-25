package com.openelements.spring.base.services.email;

/** Unchecked exception thrown by {@link EmailService} for any email-related failure. */
public class EmailException extends RuntimeException {

  public EmailException(final String message) {
    super(message);
  }

  public EmailException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
