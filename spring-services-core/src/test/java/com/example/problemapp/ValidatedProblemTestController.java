package com.example.problemapp;

import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A controller annotated {@code @Validated}, which makes Spring validate its parameters through AOP
 * and raise a {@code ConstraintViolationException} instead of Spring MVC's own {@code
 * HandlerMethodValidationException}.
 *
 * <p>Both routes have to end in the same {@code invalid-parameter} problem, which is the point of
 * the controller-boundary check in the catch-all resolver.
 */
@RestController
@Validated
public class ValidatedProblemTestController {

  /** Creates the controller. */
  public ValidatedProblemTestController() {}

  /**
   * Rejects a size above 100 through AOP method validation.
   *
   * @param size the requested page size
   * @return a constant body; only the failure path is under test
   */
  @GetMapping("/api/validated/param")
  public String param(@RequestParam @Max(100) final int size) {
    return "ok";
  }
}
