package com.openelements.spring.base.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

/**
 * Unit tests for {@link BodyErrors} — the {@code errors} array of a {@code validation-failed}
 * problem.
 *
 * <h2>What is tested</h2>
 *
 * <p>The branch Bean Validation never reaches: a failure reported by a plain Spring {@link
 * org.springframework.validation.Validator}, which carries no {@code ConstraintViolation} to unwrap
 * and therefore no property path to resolve through Jackson. The JSON names of the request cannot be
 * recovered there, so the field expression is converted structurally instead.
 *
 * <p>That fallback is invisible in every other test in this package — they all drive Hibernate
 * Validator, which always attaches a violation — and it is exactly the kind of branch that rots
 * unnoticed until an application registers its own validator and starts receiving entries with no
 * {@code pointer} at all.
 *
 * <p>Also pinned here: {@code pointer} comes before {@code message} in every entry, so two responses
 * for the same failure are byte-identical.
 *
 * <h2>How it is tested</h2>
 *
 * <p>Plain JUnit 5 against a {@link BeanPropertyBindingResult} populated the way a Spring validator
 * populates it. No mocks, no Spring context.
 */
@DisplayName("BodyErrors")
class BodyErrorsTest {

  private final BodyErrors bodyErrors = new BodyErrors(new JsonPointers(new ObjectMapper()));

  private static Errors rejecting(final String field, final String message) {
    final BeanPropertyBindingResult result =
        new BeanPropertyBindingResult(new Order(), "order");
    if (field == null) {
      result.reject("order.invalid", message);
    } else {
      result.rejectValue(field, "field.invalid", message);
    }
    return result;
  }

  @Test
  @DisplayName("A field expression without a constraint violation becomes a structural pointer")
  void fieldExpressionBecomesAPointer() {
    assertThat(bodyErrors.from(rejecting("name", "must not be blank")))
        .singleElement()
        .isEqualTo(Map.of("pointer", "/name", "message", "must not be blank"));
  }

  @Test
  @DisplayName("Nested fields and list indexes become pointer segments")
  void nestedFieldsAndIndexesBecomeSegments() {
    assertThat(bodyErrors.from(rejecting("address.street", "must not be blank")))
        .singleElement()
        .extracting(entry -> entry.get("pointer"))
        .isEqualTo("/address/street");
    assertThat(bodyErrors.from(rejecting("items[0].name", "too long")))
        .singleElement()
        .extracting(entry -> entry.get("pointer"))
        .isEqualTo("/items/0/name");
  }

  @Test
  @DisplayName("A global error without a field yields the empty pointer")
  void globalErrorYieldsTheEmptyPointer() {
    assertThat(bodyErrors.from(rejecting(null, "the range is inverted")))
        .singleElement()
        .isEqualTo(Map.of("pointer", "", "message", "the range is inverted"));
  }

  @Test
  @DisplayName("An error without a message falls back to its code rather than writing null")
  void missingMessageFallsBackToTheCode() {
    final BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Order(), "order");
    result.rejectValue("name", "field.invalid");

    assertThat(bodyErrors.from(result))
        .singleElement()
        .extracting(entry -> entry.get("message"))
        .asString()
        .isNotBlank()
        .isNotEqualTo("null");
  }

  @Test
  @DisplayName("Every entry keeps pointer before message, so the JSON is deterministic")
  void memberOrderIsDeterministic() {
    assertThat(bodyErrors.from(rejecting("name", "must not be blank")))
        .singleElement()
        .satisfies(entry -> assertThat(entry.keySet()).containsExactly("pointer", "message"));
  }

  @Test
  @DisplayName("Several failures become several entries, in the order reported")
  void severalFailuresBecomeSeveralEntries() {
    final BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Order(), "order");
    result.rejectValue("name", "field.invalid", "must not be blank");
    result.rejectValue("address.street", "field.invalid", "must not be blank");

    assertThat(bodyErrors.from(result))
        .extracting(entry -> entry.get("pointer"))
        .containsExactly("/name", "/address/street");
  }

  /** A mutable bean, because {@link BeanPropertyBindingResult} resolves field expressions on it. */
  static class Order {

    private String name = "";

    private Address address = new Address();

    private java.util.List<Item> items = java.util.List.of(new Item());

    public String getName() {
      return name;
    }

    public Address getAddress() {
      return address;
    }

    public java.util.List<Item> getItems() {
      return items;
    }
  }

  /** Nested object of {@link Order}. */
  static class Address {

    private String street = "";

    public String getStreet() {
      return street;
    }
  }

  /** List element of {@link Order}. */
  static class Item {

    private String name = "";

    public String getName() {
      return name;
    }
  }
}
