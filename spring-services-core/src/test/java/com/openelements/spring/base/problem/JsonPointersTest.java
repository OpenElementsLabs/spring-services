package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JsonPointers}.
 *
 * <h2>What is tested</h2>
 *
 * <p>That a pointer describes the <b>request the client sent</b>, not the Java object the server
 * bound it to. Everything below is a case where the two differ:
 *
 * <ul>
 *   <li>{@code @JsonProperty} renames a field;
 *   <li>a configured {@link tools.jackson.databind.PropertyNamingStrategies} renames every
 *       field — the normal case for a snake_case API, and the one where a naive pointer is wrong for
 *       every single error;
 *   <li>list indexes and map keys become pointer segments;
 *   <li>a map key containing {@code /} or {@code ~} has to be escaped per RFC 6901 §3, or the
 *       pointer silently addresses a different location;
 *   <li>a class-level constraint has no property of its own and points at the object it sits on —
 *       {@code ""} for the body root.
 * </ul>
 *
 * <h2>How it is tested</h2>
 *
 * <p>Against a real {@link Validator}: the paths come from Hibernate Validator exercising real
 * constraints on real objects, not from hand-built {@code Path} instances. A hand-built path would
 * test the author's idea of what Bean Validation produces rather than what it actually produces —
 * which is the entire risk in this class.
 *
 * <p><b>Mock-Audit.</b> No mocks. One real {@link ValidatorFactory} and real {@link ObjectMapper}s.
 */
@DisplayName("JsonPointers")
class JsonPointersTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  private final JsonPointers pointers = new JsonPointers(new JsonMapper());

  @BeforeAll
  static void startValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void stopValidator() {
    validatorFactory.close();
  }

  /**
   * Validates {@code value} and maps every violation to its pointer.
   *
   * @param value the object to validate
   * @param jsonPointers the mapper under test
   * @return the produced pointers
   */
  private static Set<String> pointersFor(final Object value, final JsonPointers jsonPointers) {
    final Set<ConstraintViolation<Object>> violations = validator.validate(value);
    assertThat(violations).as("the fixture must actually violate a constraint").isNotEmpty();
    return violations.stream()
        .map(violation -> jsonPointers.pointer(value.getClass(), violation.getPropertyPath()))
        .collect(Collectors.toSet());
  }

  private Set<String> pointersFor(final Object value) {
    return pointersFor(value, pointers);
  }

  @Test
  @DisplayName("A nested property yields a segment per level.")
  void nestedPropertyYieldsOneSegmentPerLevel() {
    final Order order = new Order(new Address("", "12345"), List.of(), Map.of());

    assertThat(pointersFor(order)).containsExactly("/address/street");
  }

  @Test
  @DisplayName("@JsonProperty renames the segment to the name the client sent.")
  void jsonPropertyRenamesTheSegment() {
    final Order order = new Order(new Address("Main Street", "ABC"), List.of(), Map.of());

    assertThat(pointersFor(order)).containsExactly("/address/postal_code");
  }

  /**
   * The strategy is configured on the mapper, not on the class — exactly as {@code
   * spring.jackson.property-naming-strategy=SNAKE_CASE} does it. A pointer that ignored the mapper
   * would be wrong for every field of such an application.
   */
  @Test
  @DisplayName("A configured naming strategy renames every segment.")
  void namingStrategyRenamesEverySegment() {
    final ObjectMapper snakeCase =
        JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    final Snake snake = new Snake("");

    assertThat(pointersFor(snake, new JsonPointers(snakeCase))).containsExactly("/postal_code");
  }

  @Test
  @DisplayName("A list index becomes its own segment.")
  void listIndexBecomesASegment() {
    final Order order =
        new Order(
            new Address("Main Street", "12345"), List.of(new Item("much too long a name")), Map.of());

    assertThat(pointersFor(order)).containsExactly("/items/0/name");
  }

  @Test
  @DisplayName("A map key becomes its own segment.")
  void mapKeyBecomesASegment() {
    final Order order =
        new Order(new Address("Main Street", "12345"), List.of(), Map.of("colour", ""));

    assertThat(pointersFor(order)).containsExactly("/labels/colour");
  }

  @Test
  @DisplayName("A map key containing '/' and '~' is escaped per RFC 6901.")
  void mapKeyIsEscaped() {
    final Order order =
        new Order(new Address("Main Street", "12345"), List.of(), Map.of("a/b~c", ""));

    assertThat(pointersFor(order)).containsExactly("/labels/a~1b~0c");
  }

  @Test
  @DisplayName("A class-level constraint on the body root yields the empty pointer.")
  void classLevelConstraintOnTheRootYieldsEmptyPointer() {
    assertThat(pointersFor(new DateRange(2, 1))).containsExactly("");
  }

  @Test
  @DisplayName("A class-level constraint on a nested object yields that object's pointer.")
  void classLevelConstraintOnANestedObjectYieldsItsPointer() {
    assertThat(pointersFor(new Booking(new DateRange(2, 1)))).containsExactly("/range");
  }

  /**
   * A field Jackson does not see at all — private, no getter, no annotation. There is no JSON name
   * to resolve, so the Java name is used. A wrong-but-present pointer still tells the client which
   * field failed; dropping the error, or emitting a pointer-shaped {@code null}, would not.
   */
  @Test
  @DisplayName("A property Jackson cannot see falls back to the Java name.")
  void invisiblePropertyFallsBackToTheJavaName() {
    assertThat(pointersFor(new Hidden())).containsExactly("/secret");
  }

  // --- fixtures ---------------------------------------------------------------------------------

  /** The request body root. */
  record Order(
      @Valid Address address, @Valid List<Item> items, Map<String, @NotBlank String> labels) {}

  /** A nested object with one renamed property. */
  record Address(
      @NotBlank String street,
      @JsonProperty("postal_code") @Pattern(regexp = "[0-9]{5}") String postalCode) {}

  /** An element of a validated list. */
  record Item(@Size(max = 5) String name) {}

  /** A body whose property is renamed by the mapper's naming strategy rather than an annotation. */
  record Snake(@NotBlank String postalCode) {}

  /** Carries a class-level constraint, so the violation has no property of its own. */
  @OrderedRange
  record DateRange(int from, int to) {}

  /** Puts a class-level-constrained object one level down. */
  record Booking(@Valid DateRange range) {}

  /** Carries a constrained field that Jackson's default visibility rules exclude. */
  static final class Hidden {
    @NotBlank private final String secret = "";
  }

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
