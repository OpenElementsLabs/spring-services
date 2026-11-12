package com.example.problemapp;

import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;

/**
 * A {@code @Validated} service, standing in for a bean deeper down the call stack.
 *
 * <p>A {@code ConstraintViolationException} raised here means the application called its own service
 * with an argument it built wrong. That is a server-side defect, not a client error — the test for
 * {@code /api/test/service-violation} pins that it becomes a {@code 500}.
 */
@Validated
public class ProblemTestService {

  /** Creates the service. */
  public ProblemTestService() {}

  /**
   * Accepts a bounded size.
   *
   * @param size must not exceed 100
   */
  public void process(@Max(100) final int size) {
    // nothing to do; the constraint is the point
  }
}
