package com.openelements.spring.base.problem;

/**
 * Names of the problem members the library writes itself.
 *
 * <p>They are listed here rather than inline so the factory that writes them and {@link
 * ProblemException}, which refuses to let an extension overwrite them, cannot drift apart.
 */
final class ProblemMembers {

  /** Correlates the response with the log lines written while the request was handled. */
  static final String TRACE_ID = "traceId";

  /** One entry per request-body field that failed validation. */
  static final String ERRORS = "errors";

  private ProblemMembers() {
    throw new AssertionError("ProblemMembers must not be instantiated");
  }
}
