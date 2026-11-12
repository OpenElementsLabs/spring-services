package com.example.problemapp;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Request bodies for the problem-details web tests.
 *
 * <p>Each one isolates a property of {@code JsonPointers} that only shows up over HTTP: a renamed
 * field, a nested object, a validated list, a validated map, and a class-level constraint.
 */
final class ProblemTestBodies {

  private ProblemTestBodies() {
    throw new AssertionError("ProblemTestBodies must not be instantiated");
  }

  /** The body root of the validation endpoint. */
  record Order(
      @Valid Address address,
      @Valid List<Item> items,
      Map<String, @NotBlank String> labels) {}

  /** Nested object with one field renamed by {@code @JsonProperty}. */
  record Address(
      @NotBlank String street,
      @JsonProperty("postal_code") @Pattern(regexp = "[0-9]{5}") String postalCode) {}

  /** Element of a validated list. */
  record Item(@Size(max = 5) String name) {}

  /** A one-field body, for the "body and parameter together" and "malformed body" endpoints. */
  record Simple(@NotBlank String name) {}

  /** A body whose only constraint is on the document as a whole. */
  @OrderedRange
  record DateRange(int from, int to) {}

  /** Fails when {@code from} is greater than {@code to}. */
  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
  @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
  @jakarta.validation.Constraint(validatedBy = OrderedRangeValidator.class)
  public @interface OrderedRange {
    String message() default "from must not be greater than to";

    Class<?>[] groups() default {};

    Class<? extends jakarta.validation.Payload>[] payload() default {};
  }

  /** Implements {@link OrderedRange}. */
  public static class OrderedRangeValidator
      implements jakarta.validation.ConstraintValidator<OrderedRange, DateRange> {

    /** Required by Hibernate Validator, which instantiates the validator reflectively. */
    public OrderedRangeValidator() {}

    @Override
    public boolean isValid(
        final DateRange value, final jakarta.validation.ConstraintValidatorContext context) {
      return value == null || value.from() <= value.to();
    }
  }
}
